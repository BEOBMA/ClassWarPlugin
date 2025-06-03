package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class SpaceOperator : GameClass() {
    override val name = "<gray>공간 조작자"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.STRUCTURE_BLOCK
    override val weapon = SpaceOperatorsSword()

    override var skills: List<Skill> = listOf(
        SpaceOperatorsRedSkill(),
        SpaceOperatorsOrangeSkill(),
        SpaceOperatorsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        SpaceOperatorsPassive()
    )
}

class SpaceOperatorsSword : Weapon() {
    override val name = "<gray>절단검"
    override val description = listOf("<gray>")
    override val material = Material.WOODEN_SWORD
}

class SpaceOperatorsRedSkill : Skill() {
    override val name = "<blue><bold>절단"
    override val description = listOf(
        "${Keyword.Charge.string}을 40 소모하여 스킬을 강화한다.",
        "",
        "<gray>바라보는 방향으로 검을 휘둘러 적중한 모든 적에게 7의 피해를 입힌다.",
        "<gray>강화 시 대신 15의 피해를 입힌다.",
        "",
        dictionary[Keyword.Charge]!!
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 방향 검 휘두르기 / 강화 여부에 따라 피해량 변경
        return true
    }
}

class SpaceOperatorsOrangeSkill : Skill() {
    override val name = "<blue><bold>도약"
    override val description = listOf(
        "${Keyword.Charge.string}을 40 소모하여 스킬을 강화한다.",
        "",
        "<gray>바라보는 방향으로 4칸 도약하고 착지 지점에 있는 모든 적에게 7의 피해를 입힌다.",
        "<gray>강화 시 대신 도약 거리 사이에 있는 모든 적에게 10의 피해를 입힌다.",
        "",
        dictionary[Keyword.Charge]!!
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 이동 및 도약 구간 적 피해 적용
        return true
    }
}

class SpaceOperatorsYellowSkill : Skill() {
    override val name = "<blue><bold>공간절단"
    override val description = listOf(
        "${Keyword.Charge.string}을 100 소모하여 스킬을 강화한다.",
        "",
        "<gray>4칸 내의 바라보는 적과 자신을 5초간 ${Keyword.Exile.string}한다.",
        "<gray>강화 시 대신 10초간 ${Keyword.Exile.string}한다.",
        "",
        dictionary[Keyword.Charge]!!,
        dictionary[Keyword.Exile]!!
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 플레이어 및 대상 엑자일 처리
        return true
    }
}

class SpaceOperatorsPassive : Passive() {
    override val name = "<blue><bold>에너지 보존과 전환"
    override val description = listOf(
        "<gray>패시브",
        "",
        "${Keyword.Charge.string} 소모 시 5초간 <gold><bold>기본 공격 피해량이 2 증가</bold><gray>한다.",
        "",
        dictionary[Keyword.Charge]!!
    )
}