package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val AFTERGLOW_ROTATION_COOLDOWN_SECONDS = 10
private const val AFTERGLOW_TRANSFER_COOLDOWN_SECONDS = 20

class Afterglow : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler,
    org.beobma.classWarPlugin.gameClass.handler.ResonanceResourceUser {
    private lateinit var runtime: org.beobma.classWarPlugin.gameClass.afterglow.AfterglowRuntime
    override fun onBattleStart() {
        runtime = org.beobma.classWarPlugin.gameClass.afterglow.AfterglowRuntime(abilityScope)
        runtime.start()
    }
    override fun onGameTimePasses() = Unit
    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if (::runtime.isInitialized) runtime.confirmed(context)
    }
    override val classId = "afterglow"
    override val name = "<gray>잔향"
    override val rank = Rank.S
    override val classItemMaterial = Material.AMETHYST_BLOCK
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private inner class RedSkill : Skill() {
        override val definitionId = "afterglow/rotation"
        override val name = "<bold>회전"
        override val description = listOf(
            "<gray>현재 위치에서 회전하여 5칸 내의 모든 적에게 5의 피해를 입힌다.",
            "<gray>분신이 존재한다면 분신 또한 회전하여 모든 적에게 3의 피해를 입힌다.",
            "",
            "<gray>적에게 피해를 입혔다면 {keyword:Aftermath}을 20 부여한다.",
            "",
            "{keyword:Resonance}이 있는 적에게 적중 시 {keyword:Resonance}을 1 소모하고 모든 분신이 해당 적에게 돌진하여",
            "<gray>분신 당 1의 추가 피해를 입힌다."
        )
        override val cooldown = AFTERGLOW_ROTATION_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.spin()
        }
    }

    private inner class OrangeSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "afterglow/transfer"
        override val name = "<bold>전이"
        override val description = listOf(
            "<gray>12칸 내의 바라보는 분신과 자신의 위치를 교환한다.",
            "<gray>교환 후 해당 분신은 즉시 잔향 패시브에 의한 기본 공격을 발동하고 사라진다.",
            "",
            "<gray>이 효과로 적에게 피해를 입혔다면 {keyword:Aftermath}을 10 부여한다.",
            "",
            "{keyword:Resonance}이 있는 적에게 적중 시 {keyword:Resonance}을 1 소모하고",
            "<gray>해당 분신은 기본 공격을 발동한 후에도 소멸하지 않으며 지속 시간이 초기화된다."
        )
        override val cooldown = AFTERGLOW_TRANSFER_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return runtime.swap()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>잔향"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 대상에게 {keyword:Aftermath}을 5 부여한다.",
            "<gray>이후 자신의 위치에 분신이 남는다. (최대 5개)",
            "<gray>분신은 5초 후 3칸 내의 가장 가까운 적에게 기본 공격을 시전하고 사라진다.",
            "<gray>이 효과로 시전된 기본 공격은 기존 피해의 50%의 피해를 입힌다."
        )
    }
}
