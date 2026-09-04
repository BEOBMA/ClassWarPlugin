package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OtherSkillUseHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import java.util.UUID

class Anchor : GameClass() {
    override val classId = "anchor"
    override val name = "<gray>닻"
    override val rank = Rank.C
    override val classItemMaterial = Material.ANVIL
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    private inner class Passive : BasePassive(), OtherSkillUseHandler, GameStatusHandler {
        override val name = "<bold>닻"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>6칸 내에서 다른 플레이어가 이동 스킬을 사용하면",
            "<gray>5초간 해당 플레이어의 <gold><bold>이동 속도가 50% 감소</bold><gray>한다.",
            "<gray>이 효과는 대상 당 20초의 재사용 대기 시간을 가진다."
        )
        private val cooldownUntil = mutableMapOf<UUID, Long>()

        override fun onBattleStart() = cooldownUntil.clear()
        override fun onGameTimePasses() = Unit

        override fun onOtherPlayerSkillUse(event: PlayerSkillUseEvent) {
            if (event.skill !is MovementSkill) return
            val target = event.playerData
            if (target.game !== game || target.entityStatus.isDead || target.player.world != player.world) return
            if (player.boundingBox.center.distanceSquared(target.player.boundingBox.center) > 36.0) return
            val now = game.combatTick
            if ((cooldownUntil[target.uniqueId] ?: Long.MIN_VALUE) > now) return
            cooldownUntil[target.uniqueId] = now + 400L
            target.getOrCreateStatus(playerData) { AnchorSlowStatus() }
                .applyStatus(duration = 5, powerSet = 50)
            particles.spawn(target.player, Particle.ASH, count = 28, spread = 0.55, speed = 0.025)
            sounds.play(target.player, Sound.BLOCK_ANVIL_PLACE, volume = 0.55f, pitch = 0.58f)
            sounds.play(player, Sound.BLOCK_CHAIN_PLACE, volume = 0.55f, pitch = 0.72f)
        }
    }
}

private class AnchorSlowStatus : MoveSpeedDecrease() {
    override val name = "<dark_gray><bold>닻의 구속</bold><gray>"
}
