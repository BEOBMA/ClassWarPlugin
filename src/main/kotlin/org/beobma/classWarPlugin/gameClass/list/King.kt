package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class King : GameClass() {
    override val classId = "king"
    override val name = "<gray>왕"
    override val rank = Rank.A
    override val classItemMaterial = Material.GOLDEN_SWORD
    override var skills: List<Skill> = listOf(

    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class Passive : BasePassive() {
        override val name = "<bold>영토 선포"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>한 청크에 10초 이상 머무를 경우, 해당 청크를 자신의 영토로 선포한다.",
            "<gray>자신은 영토 안에서 아래의 효과를 모두 얻는다.",
            "<gray>  - 가하는 피해가 (선포한 영토의 수 x 5)% 증가한다.",
            "<gray>  - 받는 피해가 (선포한 영토의 수 / 10) 감소한다. (원래 피해량의 50%를 초과할 수 없음)",
            "<gray>  - 이동 속도가 (선포한 영토의 수 x 10)% 증가한다."
        )
    }
}
