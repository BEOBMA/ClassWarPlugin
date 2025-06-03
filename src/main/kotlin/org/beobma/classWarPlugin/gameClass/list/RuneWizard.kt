package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class RuneWizard : GameClass() {
    override val name = "<gray>룬마법사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.ECHO_SHARD
    override val weapon = RuneWizardsCrystal()

    override var skills: List<Skill> = listOf(
        RuneWizardsRedSkill(),
        RuneWizardsOrangeSkill(),
        RuneWizardsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        RuneWizardsPassive()
    )
}

class RuneWizardsCrystal : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.DIAMOND
}

class RuneWizardsRedSkill : Skill() {
    override val name = "<blue><bold>마나 순환"
    override val description = listOf(
        "${Keyword.Mana.string}를 200 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 마나를 불태워 주위 모든 적에게 5의 피해를 입힌다.",
        "<gray>스킬 적중 시 소모한 ${Keyword.Mana.string}의 50%를 돌려받는다."
    )
    override val cooldown = 5

    override fun use(): Boolean {
        // TODO: 마나 소모 및 범위 피해 후 50% 마나 회복
        return true
    }
}

class RuneWizardsOrangeSkill : Skill() {
    override val name = "<blue><bold>방출"
    override val description = listOf(
        "${Keyword.Mana.string}를 500 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 4칸 내의 바라보는 적에게 8의 피해를 입힌다."
    )
    override val cooldown = 5

    override fun use(): Boolean {
        // TODO: 4칸 내 대상에게 피해
        return true
    }
}

class RuneWizardsYellowSkill : Skill() {
    override val name = "<blue><bold>인피니티"
    override val description = listOf(
        "${Keyword.Mana.string}를 1000 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 10초간 ${Keyword.Mana.string}를 무한히 사용할 수 있게 된다.",
        "<gray>이 효과가 지속되는 동안 ${Keyword.Mana.string}를 회복할 수 없다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 10초간 마나 무제한, 회복 불가
        return true
    }
}

class RuneWizardsPassive : Passive() {
    override val name = "<blue><bold>룬의 힘"
    override val description = listOf(
        "<gray>패시브",
        "",
        "${Keyword.Mana.string} 최대치가 대폭 증가한다.",
        "<gray>전투 시작 시 현재 ${Keyword.Mana.string}가 300으로 설정된다.",
        "<gray>스킬 적중 시 소모한 ${Keyword.Mana.string}의 50% 만큼 추가로 소모하고 피해를 ${Keyword.TrueDamage.string}로 전환한다.",
        "",
        dictionary[Keyword.TrueDamage]!!
    )
}
