package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Invincibility
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import kotlin.math.ceil

private const val MARS_DASH_COOLDOWN_SECONDS = 40

class Mars : PlanetClass() {
    private var solarCopy = false
    internal fun asSolarCopy(): Mars = apply { solarCopy = true }
    override val name = "<gray>화성"
    override val rank = Rank.A
    override val classItemMaterial = Material.RED_SAND
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = emptyList()
    private var dashing = false

    private inner class RedSkill : Skill() {
        override val id: String
            get() = if (solarCopy) "${javaClass.name}:solar" else javaClass.name
        override val name = "<bold>화성"
        override val description = listOf(
            "<gray>바라보는 방향으로 10칸 돌진한다.",
            "<gray>돌진 중 충돌한 모든 적에게 4의 피해를 입힌다.", "",
            "<gray>돌진 중 자신은 {keyword:Invincibility} 상태가 되며",
            "<gray>돌진 종료 후 자신은 5초간 <aqua><bold>4의 피해를 막는 {keyword:Shield}을 얻는다."
        )
        override val cooldown = MARS_DASH_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (!isPowerEnabled()) {
                player.sendMiniMessage("<red><bold>[!] 화성이 파괴되어 능력을 사용할 수 없습니다.")
                return false
            }
            if (dashing) {
                player.sendMiniMessage("<red><bold>[!] 이미 돌진 중입니다.")
                return false
            }
            return true
        }

        override fun use() {
            dashing = true
            val direction = player.eyeLocation.direction.normalize()
            val start = player.location.clone()
            val rayStart = player.boundingBox.center.toLocation(player.world)
            val dashRange = ClassBalanceManager.scaleRange(playerData, 10.0)
            val obstruction = player.world.rayTraceBlocks(
                rayStart, direction, dashRange, FluidCollisionMode.NEVER, true
            )
            val distance = ((obstruction?.hitPosition?.distance(rayStart.toVector()) ?: dashRange) - 0.75)
                .coerceIn(0.0, dashRange)
            val steps = ceil(distance / 1.1).toInt().coerceAtLeast(1)
            val step = direction.clone().multiply(distance / steps)
            val invincibility = playerData.getOrCreateStatus(playerData) { Invincibility() }
                .also { it.applyStatus(duration = 2, powerSet = 1) }
            val hit = mutableSetOf<UUID>()

            particles.spawn(player, Particle.EXPLOSION, count = 1)
            sounds.play(player, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.8f, pitch = 0.72f)
            playerData.trackTask(object : BukkitRunnable() {
                var currentStep = 0
                var previousCenter = player.boundingBox.center
                override fun run() {
                    if (!player.isOnline || playerStatus.isDead || !isPowerEnabled() || currentStep >= steps) {
                        finishDash(invincibility)
                        cancel()
                        return
                    }
                    val destination = start.clone().add(step.clone().multiply((currentStep + 1).toDouble())).apply {
                        yaw = start.yaw
                        pitch = start.pitch
                    }
                    player.teleport(destination)
                    val currentCenter = player.boundingBox.center
                    playerData.radius(currentCenter.toLocation(player.world), TargetType.Enemy, 2.0, false)
                        .filter { it.entity.uniqueId !in hit }
                        .filter { HitboxUtil.intersectsSegment(it.entity.boundingBox, previousCenter, currentCenter, 0.65) }
                        .forEach { target ->
                            hit += target.entity.uniqueId
                            target.damage(4.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                            particles.spawn(target.entity, Particle.CRIT, count = 24, spread = 0.5, speed = 0.13)
                            sounds.play(target.entity, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, volume = 0.7f, pitch = 0.78f)
                        }
                    particles.spawn(currentCenter.toLocation(player.world), Particle.DUST_PLUME, count = 12, spread = 0.48, speed = 0.09)
                    particles.spawn(currentCenter.toLocation(player.world), Particle.FLAME, count = 5, spread = 0.35, speed = 0.05)
                    previousCenter = currentCenter
                    currentStep++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }
    }

    private fun finishDash(invincibility: Invincibility) {
        if (!dashing) return
        dashing = false
        if (invincibility in playerData.statusAbnormalitys) invincibility.remove()
        if (isPowerEnabled() && !playerStatus.isDead) {
            playerData.getOrCreateStatus(playerData) { Shield() }
                .applyStatus(duration = 5, powerDelta = 4)
            particles.spawn(player, Particle.END_ROD, count = 22, spread = 0.58, speed = 0.08)
            sounds.play(player, Sound.ITEM_SHIELD_BLOCK, volume = 0.75f, pitch = 1.25f)
        }
    }
}
