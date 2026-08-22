package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class TimeManiqulator : GameClass() {
    override val name = "<gray>시간 조작자"
    override val rank = Rank.A
    override val classItemMaterial = Material.CLOCK

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>체크포인트"
        override val description = listOf(
            "<gray>현재 위치와 체력을 15초간 체크포인트로 저장한다.",
            "",
            "<gray>체크포인트가 유지되는 동안 회귀를 사용하여 저장한 위치와 체력으로 돌아갈 수 있다."
        )
        override val cooldown = 35

        override fun use() {
            //TODO()
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>회귀"
        override val description = listOf(
            "<gray>저장된 체크포인트가 있을 때에만 사용할 수 있다.",
            "",
            "<gray>체크포인트를 불러온다.",
            "<gray>사용 후 체크포인트는 제거된다."
        )
        override val cooldown = 1

        override fun use() {
            //TODO()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>시간 역설"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>체력이 1 미만으로 내려가는 피해를 받을 때, 사망을 {keyword:Invalidity}로 하고 체크포인트를 불러온다.",
            "<gray>이 효과로 불러온 경우 체력을 불러오는 효과가 50%로 감소한다.",
            "<gray>이후 체크포인트는 제거된다."
        )
    }
}
