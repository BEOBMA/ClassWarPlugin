package org.beobma.classWarPlugin.gameClass.list.dummy

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Dummy : GameClass() {
    override val name = "<gray>더미"
    override val rank = Rank.C
    override val classItemMaterial = Material.BLACK_CONCRETE
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>더미 스킬"
        override val description = listOf(
            "<gray>더미 설명"
        )
        override val cooldown = 35

        override fun use() {
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>더미 스킬"
        override val description = listOf(
            "<gray>더미 설명"
        )
        override val cooldown = 35

        override fun use() {
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>더미 패시브"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>더미 설명"
        )
    }
}
