package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Priests : GameClass() {
    override val name = "<gray>사제"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.SWEET_BERRIES
    override val weapon = PriestsStaff()

    override var skills: List<Skill> = listOf(
        PriestsRedSkill(),
        PriestsOrangeSkill(),
        PriestsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        PriestsPassive()
    )
}

class PriestsStaff : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class PriestsRedSkill : Skill() {
    override val name = "<gold><bold>치유"
    override val description = listOf(
        "<gray>바라보는 아군의 <green><bold>체력을 5 회복</bold><gray>시킨다.",
        "",
        "<dark_gray>바라보는 대상이 없으면 자신에게 시전한다."
    )
    override val cooldown = 7

    override fun use(): Boolean {
        // TODO: 대상 아군 체력 5 회복. 없으면 자신에게
        return true
    }
}

class PriestsOrangeSkill : Skill() {
    override val name = "<white><bold>정화"
    override val description = listOf(
        "<gray>바라보는 아군의 모든 해로운 상태 이상을 제거한다.",
        "",
        "<dark_gray>바라보는 대상이 없으면 자신에게 시전한다."
    )
    override val cooldown = 20

    override fun use(): Boolean {
        // TODO: 바라보는 아군의 해로운 상태 이상 제거
        return true
    }
}

class PriestsYellowSkill : Skill() {
    override val name = "<yellow><bold>죽음 방비"
    override val description = listOf(
        "<gray>바라보는 아군은 5초간 사망하지 않는다.",
        "",
        "<dark_gray>바라보는 대상이 없으면 자신에게 시전한다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 대상 아군에게 5초간 죽음 방지 효과
        return true
    }
}

class PriestsPassive : Passive() {
    override val name = "<gold><bold>기도"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>게임 시작 시 모든 아군이 <aqua><bold>4의 피해를 막는 보호막</bold><gray>을 얻는다."
    )
}
