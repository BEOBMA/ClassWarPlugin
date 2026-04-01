package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Fanatics : GameClass() {
    override val name = "<gray>광신도"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.WOODEN_SWORD
    override val weapon = FanaticsSword()

    override var skills: List<Skill> = listOf(
        FanaticsRedSkill(),
        FanaticsOrangeSkill(),
        FanaticsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        FanaticsPassiveOne(),
        FanaticsPassiveTwo()
    )
}

class FanaticsSword : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class FanaticsRedSkill : Skill() {
    override val name = "<bold>스킬 1 이름"
    override val description = listOf("<gray>스킬 1 설명")
    override val cooldown = 10

    override fun use()  {
        // TODO: 스킬 효과 구현 예정
    }
}

class FanaticsOrangeSkill : Skill() {
    override val name = "<bold>스킬 2 이름"
    override val description = listOf("<gray>스킬 2 설명")
    override val cooldown = 10

    override fun use() {
        // TODO: 스킬 효과 구현 예정
    }
}

class FanaticsYellowSkill : Skill() {
    override val name = "<bold>스킬 3 이름"
    override val description = listOf("<gray>스킬 3 설명")
    override val cooldown = Int.MAX_VALUE

    override fun use() {
        // TODO: 스킬 효과 구현 예정
    }
}

class FanaticsPassiveOne : Passive() {
    override val name = "<bold>광신"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>전투 시작 20초 후, <gold><bold>지령</bold><gray>을 받는다.",
        "<gold><bold>지령</bold><gray> 수행 성공 시 <red><bold>안도감</bold><gray>을 1 얻고 새로운 <gold><bold>지령</bold><gray>을 받는다.",
        "<gold><bold>지령</bold><gray> 수행 실패 시 <red><bold>강박</bold><gray>을 1 얻고 6초 후 새로운 <gold><bold>지령</bold><gray>을 받는다."
    )

    private val publicOfficialPassiveOrdersOne = "<gray>[지령]: 10초 내에 아무 적에게 기본 공격으로 피해를 입힌다."
    private val publicOfficialPassiveOrdersTwo = "<gray>[지령]: 10초 내에 <gold><bold>변절자 표식</bold><gray>이 있는 적에게 피해를 입힌다."
    private val publicOfficialPassiveOrdersThree = "<gray>[지령]: 10초 내에 <gold><bold>변절자 표식</bold><gray>이 있는 적에게 스킬로 피해를 입힌다."
    private val publicOfficialPassiveOrdersFour = "<gray>[지령]: 10초 내에 <gold><bold>변절자 표식</bold><gray>이 있는 적을 처치한다."
    private val publicOfficialPassiveOrdersFive = "<gray>[지령]: 모든 적을 처치한다."
}

class FanaticsPassiveTwo : Passive() {
    override val name = "<bold>변절자 처단"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gold><bold>지령</bold><gray>을 받을 때, 생존한 무작위 적에게 <gold><bold>변절자 표식</bold><gray>을 부여한다.",
        "<gold><bold>변절자 표식</bold><gray>이 있는 적에게 가하는 피해가 10% 증가한다.",
        "<gold><bold>변절자 표식</bold><gray>은 최대 1명의 적에게만 존재할 수 있다."
    )
}