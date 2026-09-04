package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import kotlin.math.cos

private const val PEANUTS_VIEW_RANGE = 48.0
private const val PEANUTS_VIEW_HALF_ANGLE_DEGREES = 42.0

class Peanuts : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler {
    override val classId = "peanuts"
    override val name = "<gray>땅콩이"
    override val rank = Rank.B
    override val classItemMaterial = Material.RABBIT_HIDE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    private var speedStatus: StatusAbnormality? = null
    private var watched: Boolean? = null

    override fun onBattleStart() {
        refreshVisibilityState()
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    clearSpeed()
                    cancel()
                    return
                }
                refreshVisibilityState()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    override fun onGameTimePasses() = Unit
    override fun onGameEnd() = clearSpeed()
    override fun onPlayerDeath() = clearSpeed()

    private fun refreshVisibilityState() {
        val isWatched = game.playerDatas.asSequence().filterIsInstance<PlayerData>()
            .filter { it != playerData && it.player.isOnline && !it.entityStatus.isDead }
            .any(::canSeeMe)
        if (watched == isWatched && speedStatus?.power ?: 0 > 0) return
        watched = isWatched
        clearSpeed()
        speedStatus = if (isWatched) {
            playerData.addStatus(MoveSpeedDecrease(), playerData).also { it.applyStatus(powerSet = 90) }
        } else {
            playerData.addStatus(MoveSpeedIncrease(), playerData).also { it.applyStatus(powerSet = 173) }
        }
        if (isWatched) {
            particles.spawn(player, Particle.SMOKE, count = 10, spread = 0.35, speed = 0.02)
            sounds.playTo(player, Sound.ENTITY_ENDERMAN_STARE, volume = 0.35f, pitch = 1.75f)
        } else {
            particles.spawn(player, Particle.CLOUD, count = 12, spread = 0.4, speed = 0.06)
            sounds.playTo(player, Sound.ENTITY_RABBIT_JUMP, volume = 0.55f, pitch = 1.6f)
        }
    }

    private fun canSeeMe(observer: PlayerData): Boolean {
        if (observer.player.world != player.world || !observer.player.hasLineOfSight(player)) return false
        val eye = observer.player.eyeLocation
        if (HitboxUtil.distanceSquared(player.boundingBox, eye.toVector()) > PEANUTS_VIEW_RANGE * PEANUTS_VIEW_RANGE) return false
        val point = HitboxUtil.closestPoint(player.boundingBox, eye.toVector())
        val toTarget = point.subtract(eye.toVector())
        if (toTarget.lengthSquared() <= 1.0E-8) return true
        return eye.direction.normalize().dot(toTarget.normalize()) >= cos(Math.toRadians(PEANUTS_VIEW_HALF_ANGLE_DEGREES))
    }

    private fun clearSpeed() {
        speedStatus?.remove()
        speedStatus = null
    }

    private class Passive : BasePassive() {
        override val name = "<bold>173"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>다른 플레이어의 시야 범위에 있지 않을 때",
            "<gray>자신의 <gold><bold>이동 속도가 173% 증가</bold><gray>한다.", "",
            "<gray>다른 플레이어의 시야 범위에 있을 때",
            "<gray>자신의 <gold><bold>이동 속도가 90% 감소</bold><gray>한다."
        )
    }
}
