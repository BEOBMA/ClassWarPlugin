package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val BOMB_THROW_COOLDOWN_SECONDS = 0
private const val BOMB_DETONATE_COOLDOWN_SECONDS = 0

class BombManiac : GameClass(), org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler {
    private val runtime by lazy { org.beobma.classWarPlugin.gameClass.bomb.BombRuntime(abilityScope) }
    override fun onBattleStart() = runtime.start()
    override fun onGameTimePasses() = Unit
    override fun onSuspend() = runtime.clearForSuspend()
    override val classId = "bomb-maniac"
    override val name = "<gray>폭탄광"
    override val rank = Rank.A
    override val classItemMaterial = Material.TNT
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf()

    private inner class RedSkill : Skill() {
        override val definitionId = "bomb-maniac/throw"
        override val name = "<bold>폭탄 투척"
        override val description = listOf(
            "{keyword:Charge}을 10 소모하여 발동한다.",
            "<gray>웅크리는 동안 매 틱 충전을 1 얻으며, 최대 100까지 저장한다.",
            "",
            "<gray>바라보는 방향으로 폭탄을 투척한다.",
            "<gray>폭탄은 지면에 남으며, 최대 5개까지 존재할 수 있다."
        )
        override val cooldown = BOMB_THROW_COOLDOWN_SECONDS
        override fun isUseSuccess() = runtime.canThrow()

        override fun use(): Boolean {
            return runtime.throwBomb()
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "bomb-maniac/detonate"
        override val name = "<bold>기폭"
        override val description = listOf(
            "{keyword:Charge}을 (현재 존재하는 폭탄의 수 x 10) 소모하여 발동한다.",
            "",
            "<gray>현재 존재하는 모든 폭탄을 0.25초 후 기폭하여 각 폭탄의 {g:range:3}칸 내 적에게 (기폭 시 선택한 폭탄의 수)의 피해를 입힌다.",
            "<gray>여러 폭탄에 적중당한 적은 최초 피해 이후 피해량이 2로 고정된다."
        )
        override val cooldown = BOMB_DETONATE_COOLDOWN_SECONDS
        override fun isUseSuccess() = runtime.canDetonate()

        override fun use(): Boolean {
            return runtime.detonate()
        }
    }
}
