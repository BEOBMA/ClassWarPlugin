package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Priests : GameClass() {
    override val name = "<gray>사제"
    override val rank = Rank.C
    override val classItemMaterial = Material.SWEET_BERRIES
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>무기 이름"
        override val description = listOf("<gray>무기 설명")
        override val material = Material.WOODEN_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<gold><bold>치유"
        override val description = listOf(
            "<gray>자신의 <green><bold>체력을 5 회복</bold><gray>시킨다."
        )
        override val cooldown = 7

        override fun use() {
            // TODO: 자신의 체력 5 회복
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<white><bold>정화"
        override val description = listOf(
            "<gray>자신의 모든 해로운 상태 이상을 제거한다."
        )
        override val cooldown = 20

        override fun use() {
            // TODO: 자신의 해로운 상태 이상 제거
        }
    }

    private class YellowSkill : Skill() {
        override val name = "<yellow><bold>죽음 방비"
        override val description = listOf(
            "<gray>자신은 5초간 사망하지 않는다."
        )
        override val cooldown = Int.MAX_VALUE

        override fun use() {
            // TODO: 자신에게 5초간 죽음 방지 효과
        }
    }

    private class Passive : BasePassive() {
        override val name = "<gold><bold>기도"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 <aqua><bold>4의 피해를 막는 보호막</bold><gray>을 얻는다."
        )
    }
}
