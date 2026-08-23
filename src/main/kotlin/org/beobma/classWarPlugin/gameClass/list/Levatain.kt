package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.GameClassManager.toWeaponItemStack
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Bleeding
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val LEVATAIN_RELEASE_PER_STAGE = 100
private const val LEVATAIN_FINAL_STAGE = 3
private const val LEVATAIN_HEALTH_RELEASE_THRESHOLD = 0.5
private const val LEVATAIN_BLEEDING_DURATION_SECONDS = 3
private const val LEVATAIN_BLEEDING_POWER = 1
private const val LEVATAIN_SEALED_DAMAGE_TAKEN_MULTIPLIER = 0.8
private const val LEVATAIN_RELEASED_DAMAGE_TAKEN_MULTIPLIER = 0.7

class Levatain : GameClass(), GameStatusHandler {
    override val name = "<gray>레바테인"
    override val rank = Rank.L
    override val classItemMaterial = Material.NETHERITE_SWORD
    override val weapon: BaseWeapon
        get() = weapons[sealStage]
    override var skills: List<Skill> = listOf()
    override var passives: List<BasePassive> = listOf(ReleasePassive(), GritPassive())

    private val weapons: List<BaseWeapon> = listOf(SealedWeapon(), FirstReleaseWeapon(), SecondReleaseWeapon(), LevatainWeapon())
    private var sealStage = 0
    private var belowHalfTriggered = false
    private var belowQuarterTriggered = false
    private var finalAuraTask: BukkitTask? = null

    override fun onBattleStart() {
        finalAuraTask?.cancel()
        finalAuraTask = null
        sealStage = 0
        belowHalfTriggered = false
        belowQuarterTriggered = false
        releaseStatus().updateState(stage = 0, release = 0)
        updateWeaponItem()
        player.sendMiniMessage("<gold><bold>[레바테인]</bold> <gray>봉인된 검의 해방도가 초기화되었습니다.")
    }

    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        addRelease(1)
        checkHealthThresholds()
    }

    private fun releaseStatus(): LevatainReleaseStatus =
        playerData.getOrCreateStatus(playerData) { LevatainReleaseStatus() }

    private fun addRelease(amount: Int) {
        if (amount <= 0 || sealStage >= FINAL_STAGE || !player.isOnline || playerStatus.isDead) return
        var release = releaseStatus().power + amount
        while (release >= MAX_RELEASE && sealStage < FINAL_STAGE) {
            release -= MAX_RELEASE
            sealStage++
            unlockNextSeal()
        }
        if (sealStage >= FINAL_STAGE) release = 0
        releaseStatus().updateState(sealStage, release)
        if (amount >= 5 && sealStage < FINAL_STAGE) {
            particles.spawn(player, Particle.ENCHANT, count = (amount / 2).coerceAtMost(18), spread = 0.38, speed = 0.03)
        }
    }

    private fun unlockNextSeal() {
        updateWeaponItem()
        val center = player.boundingBox.center.toLocation(player.world)
        val color = when (sealStage) {
            1 -> Color.fromRGB(180, 180, 190)
            2 -> Color.fromRGB(80, 220, 255)
            else -> Color.fromRGB(255, 55, 20)
        }
        particles.spawn(
            center,
            Particle.DUST,
            Particle.DustOptions(color, if (sealStage == FINAL_STAGE) 2.0f else 1.45f),
            ParticleOptions.spread(90 + sealStage * 35, 1.35, 0.2),
        )
        particles.spawn(center, Particle.ENCHANT, count = 90 + sealStage * 35, spread = 1.65, speed = 0.22)
        particles.circle(center.clone().add(0.0, -0.85, 0.0), Particle.END_ROD, 1.1 + sealStage * 0.3, 36 + sealStage * 10)
        particles.circle(center.clone().add(0.0, -0.72, 0.0), Particle.ENCHANT, 1.75 + sealStage * 0.35, 48 + sealStage * 12)
        playSealReleaseAnimation(sealStage, color)
        if (sealStage == FINAL_STAGE) {
            particles.spawn(center, Particle.FLAME, count = 180, spread = 1.55, speed = 0.28)
            particles.spawn(center, Particle.LAVA, count = 18, spread = 1.0, speed = 0.12)
            particles.spawn(center, Particle.LARGE_SMOKE, count = 75, spread = 1.35, speed = 0.08)
            particles.spawn(center, Particle.FLASH, count = 5)
            particles.spawn(center, Particle.EXPLOSION, count = 4, spread = 0.6, speed = 0.05)
            sounds.play(center, Sound.ITEM_TRIDENT_THUNDER, volume = 1.35f, pitch = 0.72f)
            sounds.play(center, Sound.ENTITY_BLAZE_SHOOT, volume = 1.2f, pitch = 0.48f)
            sounds.play(center, Sound.ENTITY_WITHER_SPAWN, volume = 0.7f, pitch = 1.35f)
            startFinalAura()
        } else {
            sounds.play(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 1.0f, pitch = 0.8f + sealStage * 0.25f)
            sounds.play(center, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 0.9f, pitch = 0.75f + sealStage * 0.15f)
        }
        player.sendMiniMessage(
            "<gold><bold>[봉인 해제]</bold> <gray>검이 <white>${weapon.name}<gray>(으)로 변화했습니다."
        )
    }

    private fun playSealReleaseAnimation(stage: Int, color: Color) {
        val duration = if (stage >= FINAL_STAGE) 42 else 30
        val accentParticle = when (stage) {
            1 -> Particle.END_ROD
            2 -> Particle.ELECTRIC_SPARK
            else -> Particle.FLAME
        }
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0

            override fun run() {
                if (!player.isOnline || playerStatus.isDead || sealStage < stage || tick > duration) {
                    cancel()
                    return
                }
                val progress = tick.toDouble() / duration
                val center = player.boundingBox.center.toLocation(player.world)
                val radius = 0.45 + progress * (1.75 + stage * 0.28)
                val height = -0.75 + progress * (3.4 + stage * 0.25)
                repeat(3) { strand ->
                    val angle = tick * (0.42 + stage * 0.045) + strand * (2.0 * PI / 3.0)
                    val point = center.clone().add(cos(angle) * radius, height, sin(angle) * radius)
                    particles.spawn(
                        point,
                        Particle.DUST,
                        Particle.DustOptions(color, if (stage >= FINAL_STAGE) 1.65f else 1.25f),
                        ParticleOptions(count = 2, offsetX = 0.035, offsetY = 0.035, offsetZ = 0.035),
                    )
                    particles.spawn(point, accentParticle, count = if (stage >= FINAL_STAGE) 3 else 2, spread = 0.055, speed = 0.025)
                }

                if (tick % 3 == 0) {
                    val ringCenter = center.clone().add(0.0, -0.82 + progress * 2.2, 0.0)
                    val ringRadius = 0.65 + progress * (1.8 + stage * 0.3)
                    spawnDustRing(ringCenter, ringRadius, 28 + stage * 7, color, tick * 0.13)
                    particles.circle(ringCenter, accentParticle, ringRadius * 0.72, 20 + stage * 6)
                }
                if (tick % 6 == 0) {
                    val base = center.clone().add(0.0, -0.85, 0.0)
                    particles.line(base, base.clone().add(0.0, 3.5 + stage * 0.25, 0.0), accentParticle, spacing = 0.2)
                    particles.spawn(center, Particle.ENCHANT, count = 24 + stage * 8, spread = 1.15 + progress, speed = 0.14)
                }
                if (stage >= FINAL_STAGE && tick % 4 == 0) {
                    particles.spawn(center.clone().add(0.0, -0.4, 0.0), Particle.LAVA, count = 3, spread = radius * 0.55, speed = 0.08)
                    particles.spawn(center, Particle.LARGE_SMOKE, count = 12, spread = radius * 0.65, speed = 0.045)
                }
                if (tick == duration) {
                    particles.spawn(center, Particle.FLASH, count = if (stage >= FINAL_STAGE) 4 else 2)
                    particles.spawn(center, Particle.EXPLOSION, count = if (stage >= FINAL_STAGE) 3 else 1, spread = 0.35)
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun startFinalAura() {
        finalAuraTask?.cancel()
        val task = object : BukkitRunnable() {
            var tick = 0

            override fun run() {
                if (!player.isOnline || playerStatus.isDead || sealStage < FINAL_STAGE) {
                    finalAuraTask = null
                    cancel()
                    return
                }
                val center = player.boundingBox.center.toLocation(player.world)
                repeat(3) { strand ->
                    val angle = tick * 0.31 + strand * (2.0 * PI / 3.0)
                    val height = -0.65 + strand * 0.72 + sin(tick * 0.18 + strand) * 0.2
                    val point = center.clone().add(cos(angle) * 0.82, height, sin(angle) * 0.82)
                    particles.spawn(point, Particle.FLAME, count = 2, spread = 0.035, speed = 0.018)
                    particles.spawn(point, Particle.SOUL_FIRE_FLAME, count = 1)
                }
                particles.spawn(center, Particle.ASH, count = 5, spread = 0.72, speed = 0.012)
                if (tick % 4 == 0) {
                    particles.circle(center.clone().add(0.0, -0.82, 0.0), Particle.FLAME, 1.05, 28)
                    particles.circle(center.clone().add(0.0, -0.72, 0.0), Particle.SOUL_FIRE_FLAME, 0.66, 18)
                }
                if (tick % 10 == 0) {
                    particles.line(
                        center.clone().add(0.0, -0.82, 0.0),
                        center.clone().add(0.0, 1.25, 0.0),
                        Particle.END_ROD,
                        spacing = 0.24,
                    )
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L)
        finalAuraTask = task
        playerData.trackTask(task)
    }

    private fun spawnDustRing(center: org.bukkit.Location, radius: Double, points: Int, color: Color, rotation: Double) {
        repeat(points) { index ->
            val angle = rotation + 2.0 * PI * index / points
            val point = center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius)
            particles.spawn(point, Particle.DUST, Particle.DustOptions(color, 1.3f), ParticleOptions())
        }
    }

    private fun updateWeaponItem() {
        val weaponSlot = if (playerData.gameClasses.indexOf(this) == 1) 8 else 0
        player.inventory.setItem(weaponSlot, toWeaponItemStack())
    }

    private fun checkHealthThresholds() {
        if (!player.isOnline || playerStatus.isDead || player.health <= 0.0) return
        val maximumHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val ratio = player.health / maximumHealth
        if (!belowHalfTriggered && ratio <= LEVATAIN_HEALTH_RELEASE_THRESHOLD) {
            belowHalfTriggered = true
            addRelease(30)
            player.sendMiniMessage("<gold><bold>[해방도]</bold> <gray>체력이 처음으로 50% 이하가 되어 <yellow>+30</yellow>")
            sounds.play(player, Sound.ENTITY_PLAYER_HURT, volume = 0.65f, pitch = 0.72f)
        }
        if (!belowQuarterTriggered && ratio <= 0.25) {
            belowQuarterTriggered = true
            addRelease(60)
            player.sendMiniMessage("<gold><bold>[해방도]</bold> <gray>체력이 처음으로 25% 이하가 되어 <yellow>+60</yellow>")
            sounds.play(player, Sound.ENTITY_WITHER_HURT, volume = 0.6f, pitch = 0.88f)
        }
    }

    private inner class ReleasePassive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>봉인 해제"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>아래 조건으로 해방도를 누적할 수 있다.",
            "<gray>해방도가 100이 되면 봉인을 한 단계 해제한다.",
            "<gray>  - 적에게 피해를 입힐 때마다 +5",
            "<gray>  - 적에게 피해를 받을 때마다 +7",
            "<gray>  - 적을 직접 처치 시 +100",
            "<gray>  - 처음으로 체력이 50% 이하가 되면 +30",
            "<gray>  - 처음으로 체력이 25% 이하가 되면 +60",
            "<gray>  - 1초마다 +1",
        )

        override fun onHit(context: DamageContext) {
            addRelease(5)
        }

        override fun onAttackHit(context: DamageContext) {
            if (sealStage < FINAL_STAGE || player.inventory.itemInMainHand.type != Material.NETHERITE_SWORD) return
            context.target.entity.fireTicks = maxOf(context.target.entity.fireTicks, 60)
            context.target.getOrCreateStatus(playerData) { Bleeding() }
                .applyStatus(
                    duration = LEVATAIN_BLEEDING_DURATION_SECONDS,
                    powerDelta = LEVATAIN_BLEEDING_POWER,
                )
            val targetCenter = context.target.entity.boundingBox.center.toLocation(context.target.entity.world)
            val slashStart = player.eyeLocation.clone().add(player.location.direction.clone().multiply(0.35))
            particles.line(slashStart, targetCenter, Particle.FLAME, spacing = 0.17)
            particles.line(slashStart, targetCenter, Particle.END_ROD, spacing = 0.38)
            particles.spawn(targetCenter, Particle.FLAME, count = 55, spread = 0.68, speed = 0.13)
            particles.spawn(targetCenter, Particle.ASH, count = 34, spread = 0.58, speed = 0.04)
            particles.spawn(targetCenter, Particle.LARGE_SMOKE, count = 18, spread = 0.5, speed = 0.055)
            particles.spawn(targetCenter, Particle.LAVA, count = 5, spread = 0.42, speed = 0.08)
            particles.circle(targetCenter, Particle.FLAME, 0.85, 30)
            playLevatainHitAfterimage(context.target.entity)
            sounds.play(targetCenter, Sound.ENTITY_BLAZE_HURT, volume = 0.9f, pitch = 0.72f)
            sounds.play(targetCenter, Sound.ITEM_FIRECHARGE_USE, volume = 0.75f, pitch = 0.8f)
        }

        private fun playLevatainHitAfterimage(target: org.bukkit.entity.Entity) {
            playerData.trackTask(object : BukkitRunnable() {
                var frame = 0

                override fun run() {
                    if (!target.isValid || frame >= 6) {
                        cancel()
                        return
                    }
                    val center = target.boundingBox.center.toLocation(target.world)
                    val progress = frame / 5.0
                    val radius = 0.55 + progress * 1.35
                    particles.circle(center.clone().add(0.0, -0.15 + progress * 0.9, 0.0), Particle.FLAME, radius, 22)
                    particles.circle(center.clone().add(0.0, 0.55 + progress * 0.75, 0.0), Particle.END_ROD, radius * 0.62, 14)
                    particles.spawn(center, Particle.ASH, count = 10, spread = radius * 0.42, speed = 0.025)
                    if (frame % 2 == 0) particles.spawn(center, Particle.LAVA, count = 2, spread = radius * 0.3, speed = 0.045)
                    frame++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
        }

        override fun whenHit(context: DamageContext) {
            addRelease(7)
            playerData.trackTask(object : BukkitRunnable() {
                override fun run() = checkHealthThresholds()
            }.runTaskLater(ClassWarPlugin.instance, 1L))
        }
    }

    private inner class GritPassive : BasePassive(), WhenHitHandler {
        override val name = "<bold>초근성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>받는 피해가 20%, 무기가 레바테인 상태라면 30% 감소한다."
        )

        override fun whenHit(context: DamageContext) {
            context.addDamageTakenMultiplier(
                if (sealStage >= FINAL_STAGE) LEVATAIN_RELEASED_DAMAGE_TAKEN_MULTIPLIER
                else LEVATAIN_SEALED_DAMAGE_TAKEN_MULTIPLIER
            )
        }
    }

    private class SealedWeapon : BaseWeapon() {
        override val name = "<gray>봉인된 검"
        override val description = listOf(
            "<gray>봉인된 상태의 검.",
            "<gray>조건 만족 시 1단계, 2단계를 거쳐 레바테인으로 해방된다."
        )
        override val material = Material.STONE_SWORD
    }

    private class FirstReleaseWeapon : BaseWeapon() {
        override val name = "<white>1단계 봉인 해제"
        override val description = listOf("<gray>봉인이 한 단계 해제된 검.")
        override val material = Material.IRON_SWORD
    }

    private class SecondReleaseWeapon : BaseWeapon() {
        override val name = "<aqua>2단계 봉인 해제"
        override val description = listOf("<gray>봉인이 두 단계 해제된 검. 다음 단계에서 레바테인이 된다.")
        override val material = Material.DIAMOND_SWORD
    }

    private class LevatainWeapon : BaseWeapon() {
        override val name = "<red><bold>레바테인"
        override val description = listOf(
            "<gray>공격 적중 시 3초간 {keyword:Burn} 상태로 만들며 {keyword:Bleeding}을 1 부여한다."
        )
        override val material = Material.NETHERITE_SWORD
    }

    companion object {
        private const val MAX_RELEASE = LEVATAIN_RELEASE_PER_STAGE
        private const val FINAL_STAGE = LEVATAIN_FINAL_STAGE

        fun handleKill(killerId: java.util.UUID?) {
            val id = killerId ?: return
            val killer = Bukkit.getPlayer(id) ?: return
            val currentGame = findGameForPlayer(killer) ?: return
            val killerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
                .find { it.uniqueId == id } ?: return
            killerData.findGameClass(Levatain::class.java)?.addRelease(100)
        }
    }
}

private class LevatainReleaseStatus : StatusAbnormality() {
    override val name = "<gold><bold>해방도</bold><gray>"
    override val description = listOf("<gray>100에 도달하면 레바테인의 봉인이 한 단계 해제된다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = LEVATAIN_RELEASE_PER_STAGE
    override var duration: Int? = null

    private var stage = 0

    fun updateState(stage: Int, release: Int) {
        this.stage = stage.coerceIn(0, LEVATAIN_FINAL_STAGE)
        updatePower(release.coerceIn(0, LEVATAIN_RELEASE_PER_STAGE))
    }

    override fun actionBarText(): String {
        val stageText = when (stage) {
            0 -> "<gray>봉인"
            1 -> "<white>1단계"
            2 -> "<aqua>2단계"
            else -> "<red><bold>레바테인"
        }
        val releaseText = if (stage >= 3) "<red>MAX</red>" else "<gold>$power</gold><dark_gray>/</dark_gray><gold>100</gold>"
        return "$name: $releaseText <dark_gray>|</dark_gray> $stageText"
    }
}
