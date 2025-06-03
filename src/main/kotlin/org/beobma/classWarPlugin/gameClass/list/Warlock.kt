package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Warlock : GameClass() {
    override val name = "<gray>워락"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.GRAY_GLAZED_TERRACOTTA
    override val weapon = WarlocksWand()

    override var skills: List<Skill> = listOf(
        WarlocksRedSkill(),
        WarlocksOrangeSkill(),
        WarlocksYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        WarlocksPassive()
    )
}

class WarlocksWand : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class WarlocksRedSkill : Skill() {
    override val name = "<gray><bold>저주"
    override val description = listOf(
        "<dark_red><bold>체력을 5 소모</bold><gray>하고 사용할 수 있다.",
        "",
        "<gray>5칸 내의 바라보는 적에게 7초간 치명적 상처를 부여한다.",
        "<gray>치명적 상처가 적용중인 적은 <gold><bold>받는 피해가 10% 증가</bold><gray>하고 받은 피해의 절반 만큼 워락은 <green><bold>체력을 회복</bold><gray>한다."
    )
    override val cooldown = 14

    override fun use(): Boolean {
        // TODO: 체력 5 소모 + 대상에게 상태이상 부여
        return true
    }
}

class WarlocksOrangeSkill : Skill() {
    override val name = "<green><bold>역병"
    override val description = listOf(
        "<dark_red><bold>체력을 10 소모</bold><gray>하고 사용할 수 있다.",
        "",
        "<gray>8초간 바라보는 블럭 주위에 역병을 일으킨다.",
        "<gray>역병이 일어난 곳에 들어온 모든 적에게 초당 4의 피해를 입힌다."
    )
    override val cooldown = 20

    override fun use(): Boolean {
        // TODO: 블럭 주위 범위 데미지 + 지속 시간
        return true
    }
}

class WarlocksYellowSkill : Skill() {
    override val name = "<bold>죽음의 원"
    override val description = listOf(
        "<dark_red><bold>체력을 20 소모</bold><gray>하고 사용할 수 있다.",
        "",
        "<gray>바라보는 대상에게 10초간 죽음의 원을 만든다.",
        "<gray>죽음의 원을 기준으로 파동이 펼쳐지며 이 파동은 시간이 지날수록 넓어진다.",
        "<gray>파동에 닿은 적은 초당 2의 피해를 입으며 워락은 이 스킬로 입힌 피해의 절반 만큼 <green><bold>체력을 회복</bold><gray>한다.",
        "",
        "<dark_gray>바라보는 대상이 없다면 자신에게 시전한다.",
        "<dark_gray>죽음의 원의 기준점이 되는 대상은 피해를 입지 않는다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 타겟 중심 파동 범위 증가 및 피해 + 회복
        return true
    }
}

class WarlocksPassive : Passive() {
    override val name = "<bold>저편의 계약"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>스킬 시전 시 소모값을 지불할 수 없다면 게임당 최대 2번, 소모값을 무시하고 사용한다."
    )
}