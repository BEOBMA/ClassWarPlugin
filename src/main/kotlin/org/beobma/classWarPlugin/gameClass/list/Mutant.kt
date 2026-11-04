package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class Mutant : GameClass() {
    override val classId = "mutant"
    override val name = "<gray>돌연변이"
    override val rank = Rank.S
    override val classItemMaterial = Material.BAT_SPAWN_EGG
    override var skills: List<Skill> = listOf()

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Passive : BasePassive() {
        override val name = "<bold>변이체"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>사망 시 게임에 다시 참여한다. (최대 5회)",
            "<gray>다시 참여할 때마다 특수한 증강체를 3개 중 1개 선택하여 얻을 수 있다.",
            "",
            "<dark_gray>이 효과는 자기장이 모두 줄어든 후에는 작동하지 않는다."
        )
    }
}
