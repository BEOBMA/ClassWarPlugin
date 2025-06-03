package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material


class Paladin : GameClass() {
    override val name = "<gray>팔라딘"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.MACE
    override val weapon = PaladinsMace()

    override var skills: List<Skill> = listOf(
        PaladinsRedSkill(),
        PaladinsOrangeSkill(),
        PaladinsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        PaladinsPassive()
    )
}

class PaladinsMace : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class PaladinsRedSkill : Skill() {
    override val name = "<gold><bold>맹세"
    override val description = listOf(
        "<gray>5초간 자신의 <gold><bold>기본 공격 피해가 2 증가</bold><gray>한다.",
        "<gray>또한, 기본 공격 적중 시 ${Keyword.Brightness.string}를 1 부여한다.",
        "",
        dictionary[Keyword.Brightness]!!
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 5초간 기본 공격 피해 +2, 적중 시 밝기 1 적용
        return true
    }
}

class PaladinsOrangeSkill : Skill() {
    override val name = "<yellow><bold>빛의 강타"
    override val description = listOf(
        "<gray>3칸 내의 바라보는 적에게 (6 + 기본 공격 피해량)의 피해를 입히고 ${Keyword.Brightness.string}를 3 부여한다.",
        "",
        dictionary[Keyword.Brightness]!!
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 대상에게 피해 및 밝기 3 부여
        return true
    }
}

class PaladinsYellowSkill : Skill() {
    override val name = "<yellow><bold>빛의 방패"
    override val description = listOf(
        "<gray>10초간 다음 3번의 공격으로부터 절반의 피해를 받는다.",
        "<gray>또한, 공격자에게 ${Keyword.Brightness.string}를 2 부여한다.",
        "<gray>이 효과는 원래 피해량이 5 이상인 경우에만 발동한다.",
        "",
        dictionary[Keyword.Brightness]!!
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 10초간 3회 데미지 반감 + 조건부 반사 밝기
        return true
    }
}

class PaladinsPassive : Passive() {
    override val name = "<gold><bold>복수의 맹세"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>아군이 기본 공격 피격 시 공격자에게 표식을 부여한다.",
        "<gray>표식을 가진 적에게 기본 공격 적중 시 2의 피해를 추가로 입히고 ${Keyword.Brightness.string}를 2 부여한다.",
        "<gray>효과 발동 후 표식은 제거된다.",
        "",
        dictionary[Keyword.Brightness]!!
    )
}