package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetPlayerData
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material

class WaterWizard : GameClass() {
    override val name = "<gray>물마법사"
    override val description = listOf(
        "<blue>서포터 마법사",
        "",
        "<gray>물마법사는 스킬로 아군에게 보호막을 제공하고 적의 이동을 방해합니다."
    )
    override val classItemMaterial = Material.WATER_BUCKET
    override val weapon = WaterWizardsStaff()

    override var skills: List<Skill> = listOf(
        WaterWizardsRedSkill(),
        WaterWizardsOrangeSkill()
    )

    override var passives: List<Passive> = listOf(
        WaterWizardsPassive()
    )
}

class WaterWizardsStaff : Weapon() {
    override val name = "<gray>대인용 지팡이"
    override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
    override val material = Material.WOODEN_SWORD
}

class WaterWizardsRedSkill() : Skill() {
    override val name = "<aqua><bold>물의 결계"
    override val description = listOf(
        "${Keyword.Mana.string}를 40 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 바라보는 아군에게 <aqua><bold>8의 피해를 막는 보호막</bold><gray>을 부여한다.",
        "<dark_gray>아군을 바라보고 있지 않았다면 자신에게 시전한다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        return true
    }
}

class WaterWizardsOrangeSkill() : Skill() {
    override val name = "<aqua><bold>파도"
    override val description = listOf(
        "${Keyword.Mana.string}를 100 소모하고 사용할 수 있다.",
        "",
        "<gray>사용 시 바라보는 방향으로 파도를 일으킨다.",
        "<gray>파도는 블럭, 물에 닿을 때까지 나아가며 수평으로만 나아간다.",
        "<gray>파도에 적중한 적은 3초간 <dark_gray><bold>40% 둔화</bold><gray>되고 파도를 따라 밀려난다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        return true
    }
}

class WaterWizardsPassive : Passive() {
    override val name = "<aqua><bold>해왕성"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gold><bold>이동 속도가 20%</bold><gray> 증가한다."
    )
}