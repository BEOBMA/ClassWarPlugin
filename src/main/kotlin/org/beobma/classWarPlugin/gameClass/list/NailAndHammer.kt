package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 20

class NailAndHammer : GameClass() {
    override val classId = "nail_and_hammer"
    override val name = "<gray>못과 망치"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_NUGGET
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Weapon : BaseWeapon() {
        override val name = "<gray>망치"
        override val description = listOf(
            "<gray>기본 공격 적중 시 적에게 박힌 못을 모두 제거한다.",
            "<gray>이후 제거한 못의 개수 만큼 추가 피해를 입힌다."
        )
        override val material = Material.IRON_SWORD
    }

    private class RedSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>못 투척"
        override val description = listOf(
            "<gray>이 스킬은 최대 4회 재사용할 수 있다.",
            "",
            "<gray>바라보는 방향으로 못을 투척하여 적중한 모든 적에게 1의 피해를 입힌다.",
            "<gray>처음 적중한 적에게는 못이 박힌 상태로 남는다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }


    private class Passive : BasePassive() {
        override val name = "<bold>은빛 낙인"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>못에 박힌 적이 받는 체력 회복 효과가 40% 감소한다.",
            "<gray>못이 제거된 후에도 5초간 지속된다."
        )
    }
}
