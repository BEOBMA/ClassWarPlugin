package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Gambler : GameClass() {
    override val name = "<gray>도박사"
    override val description = listOf(
        "<yellow>특수 승리자",
        "",
        "<gray>도박사는 전투 시작 시 덱을 섞고, 덱에서 카드를 뽑아 운으로 특수 승리를 노립니다."
    )
    override val classItemMaterial = Material.PAPER
    override val weapon = GamblersToken()

    override var skills: List<Skill> = listOf(
        GamblersRedSkill(),
        GamblersOrangeSkill(),
        GamblersYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        GamblersPassive()
    )
}

class GamblersToken : Weapon() {
    override val name = "<gray>도박사의 상징"
    override val description = listOf("<gray>손에 쥔 운명의 상징.")
    override val material = Material.PAPER
}

class GamblersRedSkill : Skill() {
    override val name = "<gray><bold>손패 섞기"
    override val description = listOf(
        "<gray>패에서 ${Keyword.SpecialVictoryCard.string}를 제외한 무작위 카드 1장을 버린다.",
        "<gray>이후 덱에서 카드를 1장 뽑는다.",
        "",
        dictionary[Keyword.SpecialVictoryCard] ?: ""
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class GamblersOrangeSkill : Skill() {
    override val name = "<gray><bold>버림패"
    override val description = listOf(
        "<gray>패에서 ${Keyword.SpecialVictoryCard.string}를 제외한 무작위 카드 2장을 버린다.",
        "<gray>이후 덱에서 카드를 3장 뽑는다.",
        "",
        dictionary[Keyword.SpecialVictoryCard] ?: ""
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class GamblersYellowSkill : Skill() {
    override val name = "<gray><bold>도박수"
    override val description = listOf(
        "<gray>덱을 섞는다.",
        "<gray>이후 덱에서 카드를 5장 뽑는다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class GamblersPassive : Passive() {
    override val name = "<bold>선택과 집중"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>스킬의 효과로 패에서 카드를 버리면 아래의 효과중에서 무작위 효과가 발동한다.",
        "",
        "<gray>3초 동안 <gold><bold>이동 속도가 20% 증가</bold><gray>한다.",
        "<gray>덱에서 카드를 1장 뽑는다.",
        "<green><bold>체력을 3 회복</bold><gray>한다."
    )
}