package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class LightWizard : GameClass() {
    override val name = "<gray>빛 마법사"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.LIGHT
    override val weapon = LightWizardsStaff()

    override var skills: List<Skill> = listOf(
        LightWizardsRedSkill(),
        LightWizardsOrangeSkill()
    )

    override var passives: List<Passive> = listOf(
        LightWizardsPassive()
    )
}

class LightWizardsStaff : Weapon() {
    override val name = "<gray>지팡이 대용 검"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class LightWizardsRedSkill : Skill() {
    override val name = "<white><bold>프리즘"
    override val description = listOf(
        "${Keyword.Mana.string}를 10 소모하고 사용할 수 있다.",
        "",
        "<gray>바라보는 블럭에 십자 모양으로 빛을 반사하는 특수 프리즘을 설치한다.",
        "<gray>설치된 프리즘은 높이 차에 관계 없이 작동한다.",
        "<gray>프리즘은 최대 10개까지 존재할 수 있다."
    )
    override val cooldown = 1

    override fun use(): Boolean {
        // TODO: 프리즘 설치 로직 구현
        return true
    }
}

class LightWizardsOrangeSkill : Skill() {
    override val name = "<white><bold>빛의 광선"
    override val description = listOf(
        "${Keyword.Mana.string}를 40 소모하고 사용할 수 있다.",
        "",
        "<gray>바라보는 방향으로 빛의 광선을 발사한다.",
        "<gray>광선이 프리즘에 적중하면 십자 모양으로 빛이 반사된다.",
        "",
        "<dark_gray>흩뿌려진 광선 또한 또다시 프리즘으로 반사될 수 있다.",
        "<dark_gray>단, 빛의 광선은 같은 프리즘에 1번만 반사될 수 있다.",
        "<dark_gray>적은 처음 발사한 광선을 포함하여 여러 광선에 적중될 수 있다.",
        "<dark_gray>적중한 모든 적은 8의 피해를 입으나, 반사된 횟수에 따라 피해량이 절반으로 감소한다.",
        "<dark_gray>광선은 최소 1의 피해를 입힌다."
    )
    override val cooldown = 6

    override fun use(): Boolean {
        // TODO: 광선 발사 및 반사 처리 구현
        return true
    }
}

class LightWizardsPassive : Passive() {
    override val name = "<white><bold>루멘"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>프리즘으로 반사된 스킬 적중 시 ${Keyword.Brightness.string}를 1 부여한다.",
        "",
        dictionary[Keyword.Brightness]!!
    )
}
