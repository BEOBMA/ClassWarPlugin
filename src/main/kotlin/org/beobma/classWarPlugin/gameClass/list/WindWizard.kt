package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class WindWizard : GameClass() {
    override val name = "<gray>바람 마법사"
    override val description = listOf(
        "<blue>서포터 마법사",
        "",
        "<gray>바람 마법사는 아군의 이동을 돕고 적의 이동을 방해합니다."
    )
    override val classItemMaterial = Material.WIND_CHARGE
    override val weapon = WindWizardsStaff()

    override var skills: List<Skill> = listOf(
        WindWizardsRedSkill(),
        WindWizardsOrangeSkill(),
        WindWizardsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        WindWizardsPassive()
    )
}

class WindWizardsStaff : Weapon() {
    override val name = "<gray>대인용 지팡이"
    override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
    override val material = Material.WOODEN_SWORD
}

class WindWizardsRedSkill() : Skill() {
    override val name = "<blue><bold>윈드 필드"
    override val description = listOf(
        "${Keyword.Mana.string}를 40 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 자신의 위치에 6초간 지속되는 대기를 형성한다.",
        "<gray>대기에 닿은 모든 대상은 <gold><bold>위로 떠오른다"
    )
    override val cooldown = 10

    override fun use(): Boolean {
        return true
    }
}

class WindWizardsOrangeSkill() : Skill() {
    override val name = "<blue><bold>윈드 스톰"
    override val description = listOf(
        "${Keyword.Mana.string}를 60 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 토네이도를 만들어 발사한다.",
        "<gray>토네이도 주위 적은 <gold><bold>끌어당겨지고 고정</bold>된다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        return true
    }
}

class WindWizardsYellowSkill() : Skill() {
    override val name = "<yellow><bold>바람의 길"
    override val description = listOf(
        "${Keyword.Mana.string}를 100 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 5초간 자신과 주위 아군의 <gold><bold>이동 속도가 40%</bold><gray> 증가한다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {

        return true
    }
}

class WindWizardsPassive : Passive() {
    override val name = "<blue><bold>대기 순환"
    override val description = listOf(
        "<gray>낙하 시 낙하 속도가 감소한다."
    )
}