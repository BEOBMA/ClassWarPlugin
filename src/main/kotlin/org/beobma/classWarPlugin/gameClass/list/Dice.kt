package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 10

class Dice : GameClass() {
    override val classId = "Dice"
    override val name = "<gray>D20"
    override val rank = Rank.C
    override val classItemMaterial = Material.BLACK_CONCRETE // 플레이어 머리
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>굴림"
        override val description = listOf(
            "<gray>20면체 주사위를 4개 굴리고 저장한다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>D20"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>저장된 주사위가 있을 때, 아래 조건에 따라 저장한 순서대로 주사위를 소모하고 효과를 얻는다.",
            "",
            "<gray>ㅡ 피격 시: 주사위 값에 비례하여 받는 피해가 증감한다.",
            "<gray>ㅡ 공격 시: 주사위 값에 비례하여 가하는 피해가 증감한다.",
            "",
            "<gray>소모한 주사위가 1일 경우 대실패하여 피해량이 0이 된다.",
            "<gray>소모한 주사위가 20일 경우 대성공하여 피해량이 2배가 된다."
        )
    }
}
