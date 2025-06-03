package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Bard : GameClass() {
    override val name = "<gray>바드"
    override val description = listOf(
        "<aqua>만능형 서포터",
        "",
        "<gray>바드는 음악을 연주하여 아군에게 여러 버프를 줍니다. 상당한 숙련도를 요구합니다."
    )
    override val classItemMaterial = Material.GOAT_HORN
    override val weapon = BardsHorn()

    override var skills: List<Skill> = listOf(
        BardsRedSkill(),
        BardsOrangeSkill(),
        BardsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        BardsPassive()
    )
}

class BardsHorn : Weapon() {
    override val name = "<gray>호른"
    override val description = listOf("<gray>아무 효과도 없는 그냥 호른.")
    override val material = Material.GOAT_HORN
}

class BardsRedSkill : Skill() {
    override val name = "<bold>기민함의 선율"
    override val description = listOf(
        "<gray>일정한 박자로 소리가 재생된다.",
        "<gray>7번째 박자마다 이 스킬을 재사용하면 스킬을 지속하여 사용할 수 있다.",
        "<gray>실패 시 스킬 지속을 종료하며 재사용 대기 시간을 적용한다.",
        "",
        "<gray>사용 시 자신과 자신 주위 아군의 <gold><bold>이동 속도가 20% 증가</bold><gray>한다.",
        "<gray>연주를 지속할 때마다 지속 시간이 조금씩 늘어난다."
    )
    override val cooldown = 20

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class BardsOrangeSkill : Skill() {
    override val name = "<bold>수복의 선율"
    override val description = listOf(
        "<gray>일정한 박자로 소리가 재생된다.",
        "<gray>7번째 박자마다 이 스킬을 재사용하면 스킬을 지속하여 사용할 수 있다.",
        "<gray>실패 시 스킬 지속을 종료하며 재사용 대기 시간을 적용한다.",
        "",
        "<gray>스킬 재사용 성공 시 자신과 자신 주위 아군의 <green><bold>체력을 2 회복</bold><gray>시킨다."
    )
    override val cooldown = 20

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class BardsYellowSkill : Skill() {
    override val name = "<bold>그림자의 선율"
    override val description = listOf(
        "<gray>일정한 박자로 소리가 재생된다.",
        "<gray>7번째 박자마다 이 스킬을 재사용하면 스킬을 지속하여 사용할 수 있다.",
        "<gray>실패 시 스킬 지속을 종료하며 재사용 대기 시간을 적용한다.",
        "",
        "<gray>연주를 지속하는 한, 자신 주위 아군만 ${Keyword.Untargetability.string} 상태로 만든다.",
        "<gray>이 효과는 ${Keyword.Untargetability.string} 상태를 무시한다.",
        "",
        dictionary[Keyword.Untargetability] ?: ""
    )
    override val cooldown = 20

    override fun use(): Boolean {
        // TODO: 구현 예정
        return true
    }
}

class BardsPassive : Passive() {
    override val name = "<bold>연주 집중"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>자신이 스킬을 사용하는 동안 <gold><bold>받는 피해가 20% 감소</bold><gray>한다."
    )
}