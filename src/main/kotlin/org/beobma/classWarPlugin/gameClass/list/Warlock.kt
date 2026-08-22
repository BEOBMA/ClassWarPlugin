package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Warlock : GameClass() {
    override val name = "<gray>워락"
    override val rank = Rank.B
    override val classItemMaterial = Material.GRAY_GLAZED_TERRACOTTA

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )
    private class RedSkill : Skill() {
        override val name = "<bold>역병의 낙인"
        override val description = listOf(
            "<dark_red><bold>체력을 5 소모</bold><gray>하고 사용할 수 있다.",
            "",
            "<gray>8칸 내의 바라보는 적에게 8초간 역병의 낙인을 새긴다.",
            "<gray>역병의 낙인이 새겨진 적은 워락에게 받는 피해가 10% 증가하고",
            "<gray>자신과 주변 3칸 이내의 적에게 매초 1의 피해를 입힌다.",
        )
        override val cooldown = 20

        override fun use() {

        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>저편의 계약"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>역병의 낙인이 새겨진 적에게 피해를 입히면 입힌 피해의 50% 만큼 체력을 회복한다."
        )
    }
}
