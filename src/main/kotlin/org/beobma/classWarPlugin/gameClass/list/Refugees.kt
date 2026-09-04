package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.bukkit.Material
import org.bukkit.Particle
import kotlin.math.abs
import kotlin.math.max

class Refugees : GameClass(), GameStatusHandler {
    override val classId = "refugees"
    override val name = "<gray>피난민"
    override val rank = Rank.C
    override val classItemMaterial = Material.WHITE_HARNESS
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    override fun onBattleStart() = Unit
    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        val border = player.world.worldBorder
        val center = border.center
        val distanceInside = border.size * 0.5 - max(abs(player.x - center.x), abs(player.z - center.z))
        if (distanceInside > 10.0) return
        playerData.getOrCreateStatus(playerData) { RefugeeSpeedStatus() }
            .applyStatus(duration = 2, powerSet = 30)
        particles.spawn(player.location.clone().add(0.0, 0.25, 0.0), Particle.CLOUD, count = 7, spread = 0.28, speed = 0.035)
    }

    private class Passive : BasePassive() {
        override val name = "<bold>피난"
        override val description = listOf(
            "<gray>패시브", "", "<gray>월드보더와 자신과의 거리 차이가 10블럭 이내라면 <gold><bold>이동 속도가 30% 증가</bold><gray>한다."
        )
    }
}

private class RefugeeSpeedStatus : MoveSpeedIncrease() {
    override val name = "<white><bold>피난</bold><gray>"
}
