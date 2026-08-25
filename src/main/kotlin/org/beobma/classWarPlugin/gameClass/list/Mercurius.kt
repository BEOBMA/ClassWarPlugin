package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.scheduler.BukkitRunnable

class Mercurius : PlanetClass(), GameStatusHandler {
    override val name = "<gray>수성"
    override val rank = Rank.B
    override val classItemMaterial = Material.REDSTONE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var speedStatus: MercurySpeedStatus? = null

    override fun onBattleStart() {
        speedStatus = null
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    speedStatus?.remove()
                    cancel()
                    return
                }
                if (!isPowerEnabled()) {
                    speedStatus?.remove()
                    speedStatus = null
                    return
                }
                val status = speedStatus?.takeIf { it in playerData.statusAbnormalitys }
                    ?: playerData.getOrCreateStatus(playerData) { MercurySpeedStatus() }.also { speedStatus = it }
                status.applyStatus(duration = 2, powerSet = 10)
                if (tick++ % 5 == 0) {
                    particles.spawn(player.location.clone().add(0.0, 0.15, 0.0), Particle.ELECTRIC_SPARK, count = 4, spread = 0.32, speed = 0.025)
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    override fun onGameTimePasses() = Unit

    private class Passive : BasePassive() {
        override val name = "<bold>수성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>이동 속도가 10% 증가한다.",
            "<gray>이동 속도 감소 효과를 받지 않는다."
        )
    }
}
private class MercurySpeedStatus : MoveSpeedIncrease() {
    override val name = "<red><bold>수성의 가속</bold><gray>"
    override val isClassMechanic = true
    override val showInActionBar = false
}
