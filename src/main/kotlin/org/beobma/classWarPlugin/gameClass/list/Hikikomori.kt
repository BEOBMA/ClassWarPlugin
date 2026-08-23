package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle

class Hikikomori : GameClass(), GameStatusHandler {
    override val name = "<gray>히키코모리"
    override val rank = Rank.C
    override val classItemMaterial = Material.AXOLOTL_BUCKET
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    override fun onBattleStart() = Unit
    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        playerData.radius(player.location, TargetType.Enemy, 10.0, false).forEach { target ->
            target.getOrCreateStatus(playerData) { HikikomoriSlowStatus() }
                .applyStatus(duration = 2, powerSet = 50)
            particles.spawn(target.entity, Particle.ASH, count = 5, spread = 0.4, speed = 0.01)
        }
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>은둔"
        override val description = listOf(
            "<gray>패시브", "", "<gray>자신 주위 10칸 이내에 적이 접근하면",
            "<gray>해당 적은 <gold><bold>이동 속도가 50% 감소</bold><gray>한다.",
            "<gray>자신은 가하는 피해가 20% 감소한다."
        )
        override fun onHit(context: DamageContext) = context.addDamageDealtMultiplier(0.8)
    }
}

private class HikikomoriSlowStatus : MoveSpeedDecrease() {
    override val name = "<dark_gray><bold>은둔의 압박</bold><gray>"
}
