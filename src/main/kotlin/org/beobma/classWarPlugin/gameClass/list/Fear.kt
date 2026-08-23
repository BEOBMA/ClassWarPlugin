package org.beobma.classWarPlugin.gameClass.list

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.Duration
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val FEAR_AURA_RADIUS = 32.0
private const val FEAR_GAZE_RADIUS = 24.0
private const val SHADOW_LIFETIME_TICKS = 240
private const val SHADOW_ATTACK_DISTANCE_SQUARED = 2.25
private const val REALITY_FRACTURE_DURATION_TICKS = 45L

private enum class FearStage {
    CALM,
    UNEASY,
    DREAD,
    TERROR,
    BREAKDOWN,
}

class Fear : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>공포"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.CRYING_OBSIDIAN
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(CrawlingFear(), Despair())

    private val miniMessage = MiniMessage.miniMessage()
    private val sanity = mutableMapOf<UUID, Double>()
    private val lastHealth = mutableMapOf<UUID, Double>()
    private val knownDead = mutableSetOf<UUID>()
    private val fearStages = mutableMapOf<UUID, FearStage>()
    private val shadowCooldown = mutableMapOf<UUID, Int>()
    private val whisperCooldown = mutableMapOf<UUID, Int>()
    private val realityCooldown = mutableMapOf<UUID, Int>()
    private val activeShadows = mutableMapOf<UUID, Int>()
    private val shadowDisplays = mutableSetOf<BlockDisplay>()
    private val hallucinatedBlocks = mutableMapOf<UUID, MutableSet<Location>>()
    private var previousTime = 0L
    private var previousDaylightCycle = true
    private var elapsedSeconds = 0
    private var nightLocked = false

    override fun onBattleStart() {
        val world = player.world
        previousTime = world.time
        previousDaylightCycle = world.getGameRuleValue(GameRules.ADVANCE_TIME)
        world.time = 18000L
        world.setGameRule(GameRules.ADVANCE_TIME, false)
        nightLocked = true
        clearRuntimeState()
        game.playerDatas.filterIsInstance<PlayerData>().filter { it != playerData }.forEach {
            sanity[it.uniqueId] = 100.0
            lastHealth[it.uniqueId] = it.player.health
            fearStages[it.uniqueId] = FearStage.CALM
            if (it.entityStatus.isDead) knownDead += it.uniqueId
        }
        elapsedSeconds = 0
        particles.spawn(player, Particle.SOUL, count = 95, spread = 1.15, speed = 0.15)
        sounds.play(player, Sound.ENTITY_WARDEN_EMERGE, volume = 0.75f, pitch = 0.48f)
    }

    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        player.world.time = 18000L
        elapsedSeconds++
        val participants = game.playerDatas.filterIsInstance<PlayerData>()
        val newlyDead = participants.filter { it != playerData && it.entityStatus.isDead && knownDead.add(it.uniqueId) }
        participants.filter { it != playerData && !it.entityStatus.isDead && it.player.isOnline }.forEach { target ->
            val victim = target.player
            val previousHealth = lastHealth.put(target.uniqueId, victim.health) ?: victim.health
            val lostHealth = (previousHealth - victim.health).coerceAtLeast(0.0)
            var value = sanity[target.uniqueId] ?: 100.0
            if (lostHealth > 0.0) value -= lostHealth * 6.0

            newlyDead.forEach { deadPlayer ->
                if (deadPlayer.player.world == victim.world &&
                    victim.boundingBox.center.distanceSquared(deadPlayer.player.boundingBox.center) <= 625.0
                ) {
                    value -= 25.0
                }
            }

            value += when {
                victim.eyeLocation.block.lightLevel >= 11 -> 2.5
                victim.eyeLocation.block.lightLevel >= 8 -> 1.0
                else -> -1.5
            }
            value -= proximitySanityDrain(target)
            value = value.coerceIn(0.0, 100.0)
            sanity[target.uniqueId] = value
            applyFearEffects(target, value)
        }
    }

    override fun onPlayerDeath() {
        cleanupHallucinations()
    }

    override fun onGameEnd() {
        cleanupHallucinations()
        if (!nightLocked) return
        player.world.time = previousTime
        player.world.setGameRule(GameRules.ADVANCE_TIME, previousDaylightCycle)
        nightLocked = false
    }

    private fun proximitySanityDrain(target: PlayerData): Double {
        val victim = target.player
        if (victim.world != player.world) return 0.0
        val distanceSquared = victim.location.distanceSquared(player.location)
        if (distanceSquared > FEAR_AURA_RADIUS * FEAR_AURA_RADIUS) return 0.0
        val distance = kotlin.math.sqrt(distanceSquared)
        var drain = (1.0 - distance / FEAR_AURA_RADIUS) * 3.5
        if (distance <= FEAR_GAZE_RADIUS && victim.hasLineOfSight(player) && isLookingAt(victim.eyeLocation, player.eyeLocation)) {
            drain += 2.5
        }
        return drain
    }

    private fun isLookingAt(eyes: Location, target: Location): Boolean {
        val towardTarget = target.toVector().subtract(eyes.toVector())
        if (towardTarget.lengthSquared() <= 0.0001) return true
        return eyes.direction.normalize().dot(towardTarget.normalize()) >= 0.82
    }

    private fun applyFearEffects(target: PlayerData, value: Double) {
        val victim = target.player
        decrementCooldown(shadowCooldown, target.uniqueId)
        decrementCooldown(whisperCooldown, target.uniqueId)
        decrementCooldown(realityCooldown, target.uniqueId)

        val stage = fearStage(value)
        val previousStage = fearStages.put(target.uniqueId, stage) ?: FearStage.CALM
        if (stage.ordinal > previousStage.ordinal) triggerStageTransition(target, stage)

        if (stage >= FearStage.UNEASY && (whisperCooldown[target.uniqueId] ?: 0) <= 0) {
            val chance = when (stage) {
                FearStage.UNEASY -> 0.35
                FearStage.DREAD -> 0.52
                else -> 0.72
            }
            if (Random.nextDouble() < chance) {
                startApproachingFootsteps(target)
                whisperCooldown[target.uniqueId] = when (stage) {
                    FearStage.UNEASY -> Random.nextInt(7, 12)
                    FearStage.DREAD -> Random.nextInt(5, 9)
                    else -> Random.nextInt(3, 6)
                }
            }
        }

        if (stage >= FearStage.DREAD) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 55, 0, false, false, false))
            val heartbeatInterval = if (stage >= FearStage.TERROR) 3 else 5
            if (elapsedSeconds % heartbeatInterval == 0) {
                victim.playSound(victim.location, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 0.62f, 0.58f)
            }
            if (value <= 40.0 && elapsedSeconds % 4 == 0) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 90, 0, false, false, false))
            }
        }

        if (value <= 40.0 && (shadowCooldown[target.uniqueId] ?: 0) <= 0) {
            val maximumShadows = when {
                value <= 10.0 -> 3
                value <= 25.0 -> 2
                else -> 1
            }
            if ((activeShadows[target.uniqueId] ?: 0) < maximumShadows) {
                spawnShadow(target)
                shadowCooldown[target.uniqueId] = when {
                    value <= 10.0 -> Random.nextInt(2, 4)
                    value <= 25.0 -> Random.nextInt(3, 6)
                    else -> Random.nextInt(5, 8)
                }
            }
        }

        if (stage >= FearStage.TERROR) {
            victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 55, 0, false, false, false))
            victim.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 55, 0, false, false, false))
            if ((realityCooldown[target.uniqueId] ?: 0) <= 0 && Random.nextDouble() < 0.38) {
                fractureReality(target)
                realityCooldown[target.uniqueId] = Random.nextInt(8, 13)
            }
        }

        if (stage == FearStage.BREAKDOWN) {
            if (elapsedSeconds % 6 == 0) {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 32, 0, false, false, false))
            }
            if (Random.nextDouble() < 0.32) {
                target.damage(1.5, DamageType.StatusAbnormality, playerData, false, damagePath = DamagePath.STATUS_EFFECT)
                victim.playSound(victim.location, Sound.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.MASTER, 0.52f, 1.55f)
            }
        }
    }

    private fun triggerStageTransition(target: PlayerData, stage: FearStage) {
        val victim = target.player
        when (stage) {
            FearStage.CALM -> Unit
            FearStage.UNEASY -> {
                startApproachingFootsteps(target)
                whisperCooldown[target.uniqueId] = Random.nextInt(7, 12)
            }
            FearStage.DREAD -> {
                victim.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false))
                victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_STARE, SoundCategory.MASTER, 0.62f, 0.48f)
            }
            FearStage.TERROR -> {
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 35, 0, false, false, false))
                fractureReality(target)
                realityCooldown[target.uniqueId] = Random.nextInt(8, 13)
                spawnShadow(target)
            }
            FearStage.BREAKDOWN -> {
                victim.showTitle(
                    Title.title(
                        miniMessage.deserialize("<dark_red><bold>도망쳐"),
                        miniMessage.deserialize("<gray>뒤를 보지 마"),
                        Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(650), Duration.ofMillis(250)),
                    )
                )
                fractureReality(target)
                realityCooldown[target.uniqueId] = Random.nextInt(7, 11)
                repeat(2) { spawnShadow(target) }
                victim.playSound(victim.location, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.MASTER, 0.85f, 0.72f)
            }
        }
    }

    private fun startApproachingFootsteps(target: PlayerData) {
        val victim = target.player
        val backward = victim.eyeLocation.direction.clone().setY(0.0).let {
            if (it.lengthSquared() <= 0.0001) randomHorizontalVector() else it.normalize().multiply(-1.0)
        }
        val right = Vector(-backward.z, 0.0, backward.x)
        val sideOffset = Random.nextDouble(-2.2, 2.2)
        playerData.trackTask(object : BukkitRunnable() {
            var step = 0
            override fun run() {
                if (!victim.isOnline || target.entityStatus.isDead || step > 5) {
                    cancel()
                    return
                }
                val distance = 7.5 - step * 1.15
                val source = victim.location.clone()
                    .add(backward.clone().multiply(distance))
                    .add(right.clone().multiply(sideOffset))
                victim.playSound(source, Sound.BLOCK_GRAVEL_STEP, SoundCategory.MASTER, 0.42f + step * 0.055f, 0.55f)
                if (step == 5) {
                    victim.playSound(source, Sound.ENTITY_PLAYER_BREATH, SoundCategory.MASTER, 0.72f, 0.48f)
                }
                step++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 4L))
    }

    private fun fractureReality(target: PlayerData) {
        val victim = target.player
        if (!victim.isOnline) return
        val forward = victim.eyeLocation.direction.clone().setY(0.0).let {
            if (it.lengthSquared() <= 0.0001) randomHorizontalVector() else it.normalize()
        }
        val right = Vector(-forward.z, 0.0, forward.x)
        val wallCenter = victim.location.clone().add(forward.multiply(3.2))
        val changedLocations = mutableSetOf<Location>()
        for (horizontal in -2..2) {
            for (vertical in 0..2) {
                val blockLocation = wallCenter.clone()
                    .add(right.clone().multiply(horizontal.toDouble()))
                    .add(0.0, vertical.toDouble(), 0.0)
                    .block.location
                if (!blockLocation.block.isPassable) continue
                val material = when {
                    vertical == 1 && horizontal == 0 -> Material.CRYING_OBSIDIAN
                    (horizontal + vertical) % 2 == 0 -> Material.SCULK
                    else -> Material.BLACK_CONCRETE
                }
                victim.sendBlockChange(blockLocation, material.createBlockData())
                changedLocations += blockLocation
            }
        }
        if (changedLocations.isEmpty()) return
        hallucinatedBlocks.getOrPut(target.uniqueId) { mutableSetOf() }.addAll(changedLocations)
        victim.playSound(wallCenter, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.MASTER, 0.72f, 0.62f)
        playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                restoreHallucinatedBlocks(target, changedLocations)
            }
        }.runTaskLater(ClassWarPlugin.instance, REALITY_FRACTURE_DURATION_TICKS))
    }

    private fun restoreHallucinatedBlocks(target: PlayerData, locations: Collection<Location>) {
        if (target.player.isOnline) {
            locations.forEach { location -> target.player.sendBlockChange(location, location.block.blockData) }
        }
        hallucinatedBlocks[target.uniqueId]?.let { tracked ->
            tracked.removeAll(locations.toSet())
            if (tracked.isEmpty()) hallucinatedBlocks.remove(target.uniqueId)
        }
    }

    private fun spawnShadow(target: PlayerData) {
        val victim = target.player
        if (!victim.isOnline || target.entityStatus.isDead || victim.world != player.world) return
        val backward = victim.eyeLocation.direction.clone().setY(0.0).let {
            if (it.lengthSquared() <= 0.0001) randomHorizontalVector() else it.normalize().multiply(-1.0)
        }
        val right = Vector(-backward.z, 0.0, backward.x)
        val start = victim.location.clone()
            .add(backward.multiply(Random.nextDouble(5.5, 8.0)))
            .add(right.multiply(Random.nextDouble(-2.0, 2.0)))
            .apply { y = victim.location.y }
        val display = start.world.spawn(start, BlockDisplay::class.java).apply {
            block = Material.BLACK_CONCRETE.createBlockData()
            brightness = Display.Brightness(0, 0)
            isPersistent = false
            transformation = Transformation(
                Vector3f(-0.35f, 0f, -0.35f),
                Quaternionf(),
                Vector3f(0.7f, 1.9f, 0.7f),
                Quaternionf(),
            )
        }
        TemporaryDisplayManager.mark(display, player.uniqueId)
        shadowDisplays += display
        activeShadows[target.uniqueId] = (activeShadows[target.uniqueId] ?: 0) + 1
        Bukkit.getOnlinePlayers().filter { it.uniqueId != victim.uniqueId }.forEach {
            it.hideEntity(ClassWarPlugin.instance, display)
        }
        victim.showEntity(ClassWarPlugin.instance, display)
        victim.playSound(start, Sound.ENTITY_ENDERMAN_STARE, SoundCategory.MASTER, 0.48f, 0.46f)

        playerData.trackTask(object : BukkitRunnable() {
            var ticks = 0
            var finished = false

            override fun run() {
                if (!display.isValid || !victim.isOnline || target.entityStatus.isDead ||
                    victim.world != display.world || ticks >= SHADOW_LIFETIME_TICKS
                ) {
                    finishShadow()
                    return
                }

                val shadowCenter = display.location.clone().add(0.0, 0.95, 0.0)
                val difference = victim.eyeLocation.toVector().subtract(shadowCenter.toVector())
                if (difference.lengthSquared() <= SHADOW_ATTACK_DISTANCE_SQUARED) {
                    attackVictim()
                    finishShadow()
                    return
                }

                val towardShadow = shadowCenter.toVector().subtract(victim.eyeLocation.toVector())
                val watched = towardShadow.lengthSquared() > 0.0001 &&
                    victim.eyeLocation.direction.normalize().dot(towardShadow.normalize()) >= 0.68 &&
                    victim.hasLineOfSight(display)
                if (watched) {
                    if (ticks % 20 == 0) {
                        victim.playSound(display.location, Sound.BLOCK_CHAIN_PLACE, SoundCategory.MASTER, 0.34f, 0.52f)
                    }
                } else {
                    val currentSanity = sanity[target.uniqueId] ?: 100.0
                    val speed = when {
                        currentSanity <= 10.0 -> 0.46
                        currentSanity <= 25.0 -> 0.36
                        else -> 0.27
                    }
                    val movement = difference.normalize().multiply(speed)
                    display.teleport(display.location.add(movement.x, movement.y.coerceIn(-0.12, 0.12), movement.z))
                    if (ticks % 12 == 0) {
                        victim.playSound(display.location, Sound.BLOCK_DEEPSLATE_STEP, SoundCategory.MASTER, 0.42f, 0.45f)
                    }
                }
                ticks += 2
            }

            private fun attackVictim() {
                val currentSanity = sanity[target.uniqueId] ?: 100.0
                victim.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false))
                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 70, 0, false, false, false))
                val knockback = victim.location.toVector().subtract(display.location.toVector())
                if (knockback.lengthSquared() > 0.0001) {
                    victim.velocity = knockback.normalize().multiply(0.48).setY(0.24)
                }
                target.damage(
                    if (currentSanity <= 10.0) 2.0 else 1.0,
                    DamageType.StatusAbnormality,
                    playerData,
                    false,
                    damagePath = DamagePath.STATUS_EFFECT,
                )
                victim.playSound(victim.location, Sound.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.MASTER, 0.9f, 0.72f)
                victim.playSound(victim.location, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.MASTER, 0.58f, 0.48f)
            }

            private fun finishShadow() {
                if (finished) return
                finished = true
                shadowDisplays.remove(display)
                display.remove()
                val remaining = ((activeShadows[target.uniqueId] ?: 1) - 1).coerceAtLeast(0)
                if (remaining == 0) activeShadows.remove(target.uniqueId)
                else activeShadows[target.uniqueId] = remaining
                cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    private fun cleanupHallucinations() {
        shadowDisplays.toList().forEach(BlockDisplay::remove)
        shadowDisplays.clear()
        activeShadows.clear()
        val participants = game.playerDatas.filterIsInstance<PlayerData>().associateBy { it.uniqueId }
        hallucinatedBlocks.toMap().forEach { (playerId, locations) ->
            participants[playerId]?.let { restoreHallucinatedBlocks(it, locations.toList()) }
        }
        hallucinatedBlocks.clear()
        fearStages.clear()
        shadowCooldown.clear()
        whisperCooldown.clear()
        realityCooldown.clear()
    }

    private fun clearRuntimeState() {
        cleanupHallucinations()
        sanity.clear()
        lastHealth.clear()
        knownDead.clear()
    }

    private fun fearStage(value: Double): FearStage = when {
        value <= 10.0 -> FearStage.BREAKDOWN
        value <= 25.0 -> FearStage.TERROR
        value <= 50.0 -> FearStage.DREAD
        value <= 75.0 -> FearStage.UNEASY
        else -> FearStage.CALM
    }

    private fun decrementCooldown(cooldowns: MutableMap<UUID, Int>, playerId: UUID) {
        cooldowns[playerId] = ((cooldowns[playerId] ?: 0) - 1).coerceAtLeast(0)
    }

    private fun randomHorizontalVector(): Vector {
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        return Vector(cos(angle), 0.0, sin(angle))
    }

    private class CrawlingFear : BasePassive() {
        override val name = "<bold>기어다니는 공포"
        override val description = listOf(
            "<gray>패시브", "", "<gray>게임 시작 시 시간을 밤으로 만들고, 밤으로 고정한다.",
            "<gray>자신을 제외한 모든 플레이어는 보이지 않는 정신력을 가지고 시작한다.",
            "<gray>피해를 받거나 사망을 목격하면 정신력이 크게 감소한다.",
            "<gray>어둠 속에서는 정신력이 감소하고 밝은 곳에서는 천천히 회복한다.",
            "<gray>공포에게 가까이 가거나 공포를 직접 바라보면 정신력이 빠르게 감소한다.",
        )
    }

    private class Despair : BasePassive() {
        override val name = "<bold>절망"
        override val description = listOf(
            "<gray>패시브", "", "<gray>정신력이 감소할수록 현실과 감각이 단계적으로 무너진다.",
            "<gray>  - 보이지 않는 무언가의 발소리와 숨소리가 뒤에서 가까워진다.",
            "<gray>  - 어둠과 멀미가 지속되고, 존재하지 않는 벽이 길을 막는다.",
            "<gray>  - 이동과 공격이 둔해지며, 검은 형체가 피해자에게만 보인다.",
            "<gray>  - 검은 형체는 바라보는 동안 멈추지만 시선을 돌리면 추격하여 공격한다.",
            "<gray>  - 정신력이 완전히 붕괴하면 실명과 환각 피해가 반복된다.",
        )
    }
}
