package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Dummy : GameClass() {
    override val name = "<gray>클래스 이름"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.WOODEN_SWORD
    override val weapon = DummysSword()

    override var skills: List<Skill> = listOf(
        DummysRedSkill(),
        DummysOrangeSkill(),
        DummysYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        DummysPassive()
    )
}

class DummysSword : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class DummysRedSkill : Skill() {
    override val name = "<bold>스킬 1 이름"
    override val description = listOf("<gray>스킬 1 설명")
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class DummysOrangeSkill : Skill() {
    override val name = "<bold>스킬 2 이름"
    override val description = listOf("<gray>스킬 2 설명")
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class DummysYellowSkill : Skill() {
    override val name = "<bold>스킬 3 이름"
    override val description = listOf("<gray>스킬 3 설명")
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 스킬 효과 구현 예정
        return true
    }
}

class DummysPassive : Passive() {
    override val name = "<bold>패시브 1 이름"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>패시브 1 설명"
    )
}