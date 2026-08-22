package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Watchmaker : GameClass() {
    override val name = "<gray>시계공"
    override val rank = Rank.B
    override val classItemMaterial = Material.WOODEN_SWORD

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<Passive> = listOf(
        PassiveOne()
    )

    private class RedSkill : Skill() {
        override val name = "<bold>시계침"
        override val description = listOf(
            "<gray>현재 위치에 6초 동안 시계침을 배치하고 회전시킨다.",
            "<gray>시계침은 2초에 걸쳐 한 바퀴 회전하며",
            "<gray>각 적마다 한 번만 현재 시간대의 효과를 적용한다.",
            "",
            "<gray>여명 - 4의 피해를 입히고 자신은 5초간 <aqua><bold>4의 피해를 막는 {keyword:Shield}을 얻는다.",
            "<gray>정오 - 8의 피해를 입힌다.",
            "<gray>자정 - 4의 피해를 입히고 자신은 체력을 4 회복한다."
        )
        override val cooldown = 12

        override fun use()  {
            // TODO: 스킬 효과 구현 예정
        }
    }

    private class PassiveOne : Passive() {
        override val name = "<bold>시계"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 여명 시간대에 진입한다.",
            "<gray>12초마다 여명, 정오, 자정 순서로 시간대가 변경된다."
        )
    }
}
