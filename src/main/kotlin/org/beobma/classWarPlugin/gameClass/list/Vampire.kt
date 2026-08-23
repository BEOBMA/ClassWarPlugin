package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.BleedingDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Bleeding
import org.beobma.classWarPlugin.status.list.BleedingLock
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Bat
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val VAMPIRE_BAT_COOLDOWN_SECONDS = 60
private const val VAMPIRE_BAT_MAX_DURATION_SECONDS = 30
private const val VAMPIRE_BLOOD_PLAGUE_COOLDOWN_SECONDS = 120
private const val VAMPIRE_REFLECTED_DAMAGE_MULTIPLIER = 2.0
private const val VAMPIRE_BLOOD_PLAGUE_DURATION_SECONDS = 4

class Vampire : GameClass() {
    override val name = "<gray>흡혈귀"
    override val rank = Rank.S
    override val classItemMaterial = Material.BAT_SPAWN_EGG
    private val batSkill = RedSkill()
    override var skills: List<Skill> = listOf(batSkill, OrangeSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    private var transformed = false
    private var bat: Bat? = null
    private var batTask: BukkitTask? = null
    private var transformationSilence: Silence? = null
    private var transformationStealth: Stealth? = null
    private var savedAllowFlight = false
    private var savedFlying = false
    private var savedGravity = true
    private var savedCanAttack = true
    private var savedAttackable = true
    private var savedSkillTargeting = true

    private inner class RedSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val name = "<bold>박쥐화"
        override val description = listOf(
            "<gray>자신은 박쥐로 변신하여 날아다닐 수 있게 된다.",
            "<gray>박쥐화는 최대 ${VAMPIRE_BAT_MAX_DURATION_SECONDS}초간 유지되며, 다시 사용하면 즉시 해제된다.",
            "<gray>박쥐가 피해를 입으면 해당 피해의 2배 만큼 자신이 피해를 입는다.",
            "<gray>박쥐가 사망한 경우 이 스킬의 재사용 대기 시간이 2배로 증가하고 변신이 해제된다.", "",
            "<dark_gray>박쥐로 변신한 상태에서는 {keyword:Silence} 상태가 되며, 기본 공격을 사용할 수 없다.",
            "<dark_gray>변신 중에는 재사용 대기 시간이 흐르지 않으며, 변신이 해제된 후부터 감소한다."
        )
        override val cooldown = VAMPIRE_BAT_COOLDOWN_SECONDS
        override val isOnOffSKill = true
        override val canUseWhileSilenced: Boolean
            get() = transformed

        override fun use() {
            if (transformed) {
                endTransformation(batDied = false)
            } else {
                beginTransformation()
                multiplyCurrentCooldown(0.0)
            }
        }
    }

    private fun beginTransformation() {
        if (transformed) return
        transformed = true
        savedAllowFlight = player.allowFlight
        savedFlying = player.isFlying
        savedGravity = player.hasGravity()
        savedCanAttack = playerStatus.canAttack
        savedAttackable = playerStatus.isAttackable
        savedSkillTargeting = playerStatus.isSkillTargeting

        player.allowFlight = true
        player.isFlying = true
        player.setGravity(false)
        playerStatus.canAttack = false
        playerStatus.isAttackable = false
        playerStatus.isSkillTargeting = false
        transformationSilence = (playerData.addStatus(Silence(), playerData) as Silence).also {
            it.applyStatus(powerSet = 1)
        }
        transformationStealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also {
            it.applyStatus(powerSet = 1)
        }

        val spawnedBat = player.world.spawn(player.location.clone().add(0.0, 0.8, 0.0), Bat::class.java).apply {
            isPersistent = false
            isSilent = true
            isAwake = true
            isCollidable = true
        }
        bat = spawnedBat
        activeBats[spawnedBat.uniqueId] = this
        particles.spawn(player, Particle.SMOKE, count = 34, spread = 0.6, speed = 0.12)
        particles.spawn(player, Particle.SOUL, count = 16, spread = 0.45, speed = 0.08)
        sounds.play(player, Sound.ENTITY_BAT_TAKEOFF, volume = 1.0f, pitch = 0.65f)
        player.sendMiniMessage("<dark_red><bold>[박쥐화]</bold> <gray>비행 상태가 되었습니다. 스킬을 다시 사용하면 해제합니다.")

        batTask = playerData.trackTask(object : BukkitRunnable() {
            private var ticks = 0

            override fun run() {
                val currentBat = bat
                if (!transformed || !player.isOnline || player.isDead) {
                    endTransformation(batDied = false)
                    cancel()
                    return
                }
                if (currentBat == null || !currentBat.isValid || currentBat.isDead) {
                    endTransformation(batDied = true)
                    cancel()
                    return
                }
                if (ticks >= VAMPIRE_BAT_MAX_DURATION_SECONDS * 20) {
                    player.sendMiniMessage(
                        "<dark_red><bold>[박쥐화]</bold> <gray>최대 지속시간이 끝나 변신이 해제되었습니다."
                    )
                    endTransformation(batDied = false)
                    cancel()
                    return
                }
                currentBat.teleport(player.location.clone().add(0.0, 0.85, 0.0))
                currentBat.velocity = player.velocity
                player.fallDistance = 0f
                if (ticks % 3 == 0) {
                    particles.spawn(currentBat, Particle.SMOKE, count = 3, spread = 0.22, speed = 0.02)
                }
                ticks++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun endTransformation(batDied: Boolean) {
        if (!transformed && bat == null) return
        transformed = false
        val removedBat = bat
        if (removedBat != null) activeBats.remove(removedBat.uniqueId)
        removedBat?.remove()
        bat = null
        batTask?.cancel()
        batTask = null
        transformationSilence?.remove()
        transformationSilence = null
        transformationStealth?.remove()
        transformationStealth = null

        player.allowFlight = savedAllowFlight
        player.isFlying = savedFlying && savedAllowFlight
        player.setGravity(savedGravity)
        playerStatus.canAttack = savedCanAttack
        playerStatus.isAttackable = savedAttackable
        playerStatus.isSkillTargeting = savedSkillTargeting
        player.fallDistance = 0f
        particles.spawn(player, Particle.SMOKE, count = 26, spread = 0.55, speed = 0.1)
        sounds.play(player, if (batDied) Sound.ENTITY_BAT_DEATH else Sound.ENTITY_BAT_TAKEOFF, volume = 0.9f, pitch = if (batDied) 0.5f else 1.35f)

        if (player.isOnline && !player.isDead) {
            val skillItem = player.inventory.getItem(1) ?: ItemStack(Material.RED_DYE)
            val cooldownMultiplier = if (batDied) 2 else 1
            CooldownManager.setCooldown(
                player,
                batSkill,
                skillItem,
                batSkill.cooldown * 20 * cooldownMultiplier,
            )
            if (batDied) {
                player.sendMiniMessage("<red><bold>[박쥐 사망]</bold> <gray>박쥐화 재사용 대기 시간이 2배로 적용됩니다.")
            }
        }
    }

    private fun receiveBatDamage(event: EntityDamageEvent) {
        val currentBat = bat ?: return
        val incoming = event.finalDamage.coerceAtLeast(0.0)
        if (incoming <= 0.0) return
        val directDamager = (event as? EntityDamageByEntityEvent)?.damager
        val attackerPlayer = when (directDamager) {
            is Player -> directDamager
            is Projectile -> directDamager.shooter as? Player
            else -> null
        }
        val attackerData = attackerPlayer?.let { attacker ->
            findGameForPlayer(attacker)?.playerDatas?.filterIsInstance<PlayerData>()
                ?.find { it.uniqueId == attacker.uniqueId }
        } ?: playerData
        val path = when (directDamager) {
            is Projectile -> DamagePath.RANGED_ATTACK
            is Player -> DamagePath.BASIC_ATTACK
            else -> DamagePath.SKILL
        }

        val wasAttackable = playerStatus.isAttackable
        val wasTargeting = playerStatus.isSkillTargeting
        playerStatus.isAttackable = true
        playerStatus.isSkillTargeting = true
        try {
            playerData.damage(
                incoming * VAMPIRE_REFLECTED_DAMAGE_MULTIPLIER,
                DamageType.Normal,
                attackerData,
                damagePath = path,
            )
        } finally {
            if (transformed) {
                playerStatus.isAttackable = wasAttackable
                playerStatus.isSkillTargeting = wasTargeting
            }
        }
        particles.spawn(currentBat, Particle.DAMAGE_INDICATOR, count = 12, spread = 0.35, speed = 0.08)
        sounds.play(currentBat, Sound.ENTITY_BAT_HURT, volume = 0.85f, pitch = 0.8f)
        val remainingHealth = currentBat.health - incoming
        if (remainingHealth <= 0.0) endTransformation(batDied = true)
        else currentBat.health = remainingHealth
    }

    private inner class OrangeSkill : Skill() {
        override val name = "<bold>혈사병"
        override val description = listOf(
            "<gray>6초간 10칸 내의 범위에 혈사병을 일으킨다.",
            "<gray>지속 시간동안 범위 내의 모든 적은 {keyword:Bleeding} 수치가 감소하지 않는다.",
            "<gray>지속 시간 종료 시 한 번이라도 혈사병의 영향을 받은 모든 적의 {keyword:Bleeding}은 제거된다."
        )
        override val cooldown = VAMPIRE_BLOOD_PLAGUE_COOLDOWN_SECONDS

        override fun use() {
            val affected = mutableMapOf<UUID, EntityData>()
            val locks = mutableMapOf<UUID, Pair<EntityData, BleedingLock>>()
            sounds.play(player, Sound.ENTITY_WITHER_AMBIENT, volume = 0.75f, pitch = 1.4f)
            particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.DUST, Particle.DustOptions(Color.MAROON, 1.7f), org.beobma.classWarPlugin.effect.ParticleOptions.spread(42, 1.2, 0.12))

            playerData.trackTask(object : BukkitRunnable() {
                private var elapsedTicks = 0

                private fun finishPlague() {
                    locks.values.forEach { (_, lock) -> lock.remove() }
                    locks.clear()
                    affected.values.forEach { it.getStatus<Bleeding>()?.remove() }
                    particles.spawn(player, Particle.SQUID_INK, count = 34, spread = 1.2, speed = 0.12)
                    sounds.play(player, Sound.BLOCK_BREWING_STAND_BREW, volume = 0.9f, pitch = 0.55f)
                    cancel()
                }

                override fun run() {
                    if (!player.isOnline || playerStatus.isDead || elapsedTicks >= 120) {
                        finishPlague()
                        return
                    }
                    val inside = playerData.radius(player.location, TargetType.Enemy, 10.0, false)
                    val insideIds = inside.mapTo(mutableSetOf()) { it.entity.uniqueId }
                    inside.forEach { target ->
                        val id = target.entity.uniqueId
                        affected[id] = target
                        if (id !in locks) {
                            val lock = target.addStatus(BleedingLock(), playerData) as BleedingLock
                            lock.applyStatus(powerSet = 1)
                            locks[id] = target to lock
                        }
                        if (elapsedTicks % 5 == 0) {
                            particles.spawn(target.entity.location.clone().add(0.0, target.entity.height * 0.5, 0.0), Particle.FALLING_DUST, Material.REDSTONE_BLOCK.createBlockData(), org.beobma.classWarPlugin.effect.ParticleOptions.spread(3, 0.3, 0.02))
                        }
                    }
                    locks.keys.filter { it !in insideIds }.toList().forEach { id ->
                        locks.remove(id)?.second?.remove()
                    }
                    if (elapsedTicks % 2 == 0) drawPlagueCircle(player.location, elapsedTicks)
                    if (elapsedTicks % 20 == 0) sounds.play(player, Sound.BLOCK_SCULK_SENSOR_CLICKING, volume = 0.35f, pitch = 0.65f)
                    elapsedTicks++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }

        private fun drawPlagueCircle(center: Location, tick: Int) {
            val dust = Particle.DustOptions(Color.fromRGB(125, 0, 20), 1.15f)
            repeat(28) { index ->
                val angle = 2.0 * PI * index / 28.0 + tick * 0.025
                particles.spawn(
                    center.clone().add(cos(angle) * 10.0, 0.18, sin(angle) * 10.0),
                    Particle.DUST,
                    dust,
                    org.beobma.classWarPlugin.effect.ParticleOptions(count = 1),
                )
            }
        }
    }

    private inner class Passive : BasePassive(), OnHitHandler, BleedingDamageHandler {
        override val name = "<bold>혈귀"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>기본 공격 적중 시 4초간 {keyword:Bleeding}을 1 부여한다.", "",
            "{keyword:Bleeding} 피해를 입은 적 위치에 피가 떨어진다.",
            "<gray>떨어진 피에 다가가면 체력을 1 회복하고 피를 제거한다."
        )

        override fun onAttackHit(context: DamageContext) {
            context.target.getOrCreateStatus(playerData) { Bleeding() }
                .applyStatus(duration = VAMPIRE_BLOOD_PLAGUE_DURATION_SECONDS, powerDelta = 1)
            particles.spawn(context.target.entity.location.clone().add(0.0, context.target.entity.height * 0.5, 0.0), Particle.FALLING_DUST, Material.REDSTONE_BLOCK.createBlockData(), org.beobma.classWarPlugin.effect.ParticleOptions.spread(6, 0.3, 0.04))
            sounds.play(context.target.entity, Sound.ENTITY_PLAYER_HURT, volume = 0.35f, pitch = 0.65f)
        }

        override fun onBleedingDamage(target: EntityData, power: Int) {
            if (!player.isOnline || playerStatus.isDead || target.entity.world != player.world) return
            val ray = target.entity.world.rayTraceBlocks(
                target.entity.location.clone().add(0.0, 1.0, 0.0),
                Vector(0.0, -1.0, 0.0),
                8.0,
                FluidCollisionMode.NEVER,
                true,
            )
            val bloodLocation = ray?.hitPosition?.toLocation(target.entity.world)?.add(0.0, 0.04, 0.0)
                ?: target.entity.location.clone()
            val display = bloodLocation.world.spawn(bloodLocation, BlockDisplay::class.java).apply {
                block = Material.REDSTONE_BLOCK.createBlockData()
                isPersistent = false
                transformation = Transformation(
                    Vector3f(-0.18f, 0f, -0.18f),
                    Quaternionf(),
                    Vector3f(0.36f, 0.035f, 0.36f),
                    Quaternionf(),
                )
                TemporaryDisplayManager.mark(this, player.uniqueId)
            }
            particles.spawn(bloodLocation, Particle.FALLING_DUST, Material.REDSTONE_BLOCK.createBlockData(), org.beobma.classWarPlugin.effect.ParticleOptions.spread(8, 0.25, 0.03))
            playerData.trackTask(object : BukkitRunnable() {
                private var elapsedTicks = 0

                override fun run() {
                    if (!display.isValid || !player.isOnline || playerStatus.isDead || elapsedTicks++ >= 400) {
                        display.remove()
                        cancel()
                        return
                    }
                    if (player.world == bloodLocation.world && player.boundingBox.expand(0.65).contains(bloodLocation.toVector())) {
                        playerData.heal(1.0, DamageType.Normal, playerData)
                        particles.spawn(bloodLocation, Particle.HEART, count = 8, spread = 0.25, speed = 0.04)
                        sounds.play(player, Sound.ENTITY_GENERIC_DRINK, volume = 0.65f, pitch = 1.35f)
                        display.remove()
                        cancel()
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
        }
    }

    companion object {
        private val activeBats = mutableMapOf<UUID, Vampire>()

        fun handleBatDamage(event: EntityDamageEvent): Boolean {
            val owner = activeBats[event.entity.uniqueId] ?: return false
            event.isCancelled = true
            owner.receiveBatDamage(event)
            return true
        }

        fun clearForms(playerIds: Collection<UUID>) {
            activeBats.values.toSet()
                .filter { it.player.uniqueId in playerIds }
                .forEach { it.endTransformation(batDied = false) }
        }
    }
}
