package org.beobma.classWarPlugin.gameClass.list.dummy

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 35
private const val DUMMY_BLUE_SKILL_COOLDOWN_SECONDS = 35
private const val DUMMY_DOMAIN_SKILL_COOLDOWN_SECONDS = 300

class Dummy : GameClass() {
    override val classId = "dummy"
    override val name = "<gray>더미"
    override val rank = Rank.C
    override val classItemMaterial = Material.BLACK_CONCRETE
    override val weapon: BaseWeapon = Weapon()
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        DomainSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Weapon : BaseWeapon() {
        override val name = "<gray>커스텀 무기"
        override val description = listOf(
            "<gray>커스텀 무기 설명"
        )
        override val material = Material.IRON_SWORD
    }

    private class RedSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>더미 스킬"
        override val description = listOf(
            "<gray>더미 설명"
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class OrangeSkill : Skill() {
        override val definitionId = "dummy/orange-skill"
        override val name = "<bold>더미 스킬"
        override val description = listOf(
            "<gray>더미 설명"
        )
        override val cooldown = DUMMY_BLUE_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class DomainSkill : Skill() {
        override val definitionId = "dummy/domain-skill"
        override val name = "<bold>「영역 전개」-「더미 영역 전개 스킬」"
        override val description = listOf(
            "<gray>더미 설명"
        )
        override val cooldown = DUMMY_DOMAIN_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
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
