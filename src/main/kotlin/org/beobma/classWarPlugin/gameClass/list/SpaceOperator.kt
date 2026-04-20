package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
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
        SpaceOperatorsPassiveOne(),
        SpaceOperatorsPassiveTwo()
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
        "<gray>바라보는 방향으로 검을 휘둘러 적중한 모든 적에게 7의 피해를 입힌다."
    )
    override val cooldown = 10

    override fun use() {
        // TODO: 방향 검 휘두르기 / 강화 여부에 따라 피해량 변경
    }
}

class SpaceOperatorsOrangeSkill : Skill() {
    override val name = "<blue><bold>차원 도약"
    override val description = listOf(
        "{keyword:DimensionMarker}을 가진 적이 존재할 때에만 사용할 수 있다.",
        "",
        "<gray>사용 시 {keyword:DimensionMarker}을 가진 모든 적",
        "<gray>강화 시 대신 도약 거리 사이에 있는 모든 적에게 10의 피해를 입힌다."
    )
    override val cooldown = 10

    override fun use() {
        // TODO: 이동 및 도약 구간 적 피해 적용
    }
}

class SpaceOperatorsYellowSkill : Skill() {
    override val name = "<blue><bold>차원 균열"
    override val description = listOf(
        "<gray>사용 시 모든 적의 {keyword:DimensionMarker}를 전부 소모하고 뒤집힌 세계로 이동한다.",
        "<gray>뒤집힌 세계에서는 적에게 피해를 입힐 수 없으며, 자신은 적을 볼 수 없게 된다.",
        "<gray>소모한 {keyword:DimensionMarker} 수치에 따라 스킬이 강화된다.",
        "<gray>1 - 뒤집힌 세계에서 어떠한 피해도 받지 않는다.",
        "<gray>2 - 뒤집힌 세계에서 적에게 기본 공격으로 피해를 입히면 원래 세계로 돌아올 때 피해가 정산된다.",
        "<gray>3 - 뒤집힌 세계에서 적에게 스킬로 피해를 입히면 원래 세계로 돌아올 때 피해가 정산된다.",
        "<gray>4 - 원래 세계로 돌아올 때, 정산된 피해량이 적의 현재 체력을 초과하면 남은 피해량은 모든 적에게 균등하게 정산된다.",
        "",
        "<dark_gray>뒤집힌 세계에서는 적의 영향을 받지 않으며, 적이 보이지 않는다.",
        "<dark_gray>뒤집힌 세계에서 적에게 입힌 피해는 원래 세계로 돌아올 때 정산된다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use() {
        // TODO: 플레이어 및 대상 엑자일 처리
    }
}

class SpaceOperatorsPassiveOne : Passive() {
    override val name = "<blue><bold>차원 표식"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>전투 시작 시 무작위 적 1명에게 8초 동안 {keyword:DimensionMarker}을 1 부여한다.",
        "<gray>이후 8초마다 무작위 적 1명에게 8초 동안 {keyword:DimensionMarker}을 1 부여한다.",
        "",
        Keyword.DimensionMarker.description!!
    )
}

class SpaceOperatorsPassiveTwo : Passive() {
    override val name = "<blue><bold>4차원"
    override val description = listOf(
        "<gray>패시브",
        "",
        "{keyword:DimensionMarker}가 부여된 적에게 기본 공격, 스킬 적중 시 {keyword:DimensionMarker}의 수치가 1 증가한다.",
        "{keyword:DimensionMarker}가 최대일 때, 궁극기를 사용하면 {keyword:DimensionMarker}을 소모하고 강화된다.",
        "",
        "<dark_gray>기본 공격과 각각의 스킬이 이미 수치를 증가시킨 경우, 같은 경로로는 다시 수치를 증가시킬 수 없다.",
        Keyword.DimensionMarker.description!!
    )
}