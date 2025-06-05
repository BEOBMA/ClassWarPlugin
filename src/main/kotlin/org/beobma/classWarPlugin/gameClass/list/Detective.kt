package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Detective : GameClass() {
    override val name = "<gray>탐정"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.WOODEN_SWORD
    override val weapon = DetectivesSword()

    override var skills: List<Skill> = listOf(
        DetectivesRedSkill(),
        DetectivesOrangeSkill(),
        DetectivesYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        DetectivesPassive()
    )
}

class DetectivesSword : Weapon() {
    override val name = "<gray>돋보기"
    override val description = listOf("<gray>맞으면 아프다.")
    override val material = Material.WOODEN_SWORD
}

class DetectivesRedSkill : Skill() {
    override val name = "<bold>미행"
    override val description = listOf(
        "<gray>사용 시 3초간 ${Keyword.Stealth.string}한다.",
        "<gray>만약, 보이는 적으로부터 7칸 떨어져 있다면 지속 시간이 8초까지 증가한다."
    )
    override val cooldown = 18

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class DetectivesOrangeSkill : Skill() {
    override val name = "<bold>증거 확보"
    override val description = listOf(
        "<gray>주변 10블록 이내에 있는 모든 적의 최근 사용한 스킬 1개와 재사용 대기 시간을 확인한다.",
        "${Keyword.Stealth.string} 상태였다면, 대상의 현재 마나량과 모든 스킬의 재사용 대기 시간을 확인한다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class DetectivesYellowSkill : Skill() {
    override val name = "<bold>관찰"
    override val description = listOf(
        "<gray>사용 시 20초간 바라보는 적을 파악한다.",
        "<gray>파악한 적은 피격 시 피해량이 15% 증가한다.",
        "${Keyword.Stealth.string} 상태였다면, 피해량이 30%까지 증가하고 추가로 지속 시간동안 은신할 수 없다."
    )
    override val cooldown = 40

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class DetectivesPassive : Passive() {
    override val name = "<bold>범죄 심리학"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>5칸 이내에 적이 ${Keyword.Stealth.string}하면, ${Keyword.Stealth.string} 효과를 즉시 제거한다."
    )
}