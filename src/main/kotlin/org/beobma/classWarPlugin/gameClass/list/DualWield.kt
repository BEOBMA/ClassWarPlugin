package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class DualWield : GameClass() {
    override val classId = "dualwield"
    override val name = "<gray>쌍수"
    override val rank = Rank.A
    override val classItemMaterial = Material.DIAMOND_SWORD
    override var skills: List<Skill> = listOf()

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Passive : BasePassive() {
        override val name = "<bold>이도류"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 검을 하나 더 가지고 시작한다.",
            "<gray>검을 왼손에 장착한 상태에서 기본 공격 적중 시",
            "<gray>0.5초 후 왼손에 든 무기로 공격하여 기본 공격 피해량의 절반에 해당하는 피해를 입힌다."
        )
    }
}
