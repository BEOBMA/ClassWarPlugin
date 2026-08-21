package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Watchmaker : GameClass() {
    override val name = "<gray>시계공"
    override val rank = Rank.C
    override val classItemMaterial = Material.WOODEN_SWORD
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<Passive> = listOf(
        PassiveOne()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>무기 이름"
        override val description = listOf("<gray>무기 설명")
        override val material = Material.WOODEN_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<bold>시계침"
        override val description = listOf(
            "<gray>현재 위치에 10초 동안 시계침을 배치하고 회전시킨다.",
            "<gray>적중한 적에게 각 시간대에 따라 다른 피해, 효과를 적용한다.",
            "",
            "<gray>여명 - 4의 피해를 입히고 자신은 2의 피해를 막는 보호막을 얻는다.",
            "<gray>정오 - 6의 피해를 입히고 이 스킬로 입힌 최종 피해의 25% 만큼 추가 피해를 입힌다.",
            "<gray>자정 - 0의 피해를 입히고 자신은 체력을 6 회복한다.",
            "<gray>황혼 - 10의 피해를 입히고 모든 시간대의 효과를 1.5배로 하여 적용한다."
        )
        override val cooldown = 10

        override fun use()  {
            // TODO: 스킬 효과 구현 예정
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>시간 반전 영역"
        override val description = listOf(
            "<gray>현재 위치에 5초 동안 시간 반전 영역을 배치한다.",
            "<gray>시간 반전 영역에 위치한 자신과 적의 수에 비례하여 현재 시간대의 지속 시간이 느리게 감소한다. (1명당 25%, 최대 100%)",
            "<gray>시간 반전 영역에 시계침이 있다면 시계침이 회전하는 속도가 증가한다."
        )
        override val cooldown = 10

        override fun use() {
            // TODO: 스킬 효과 구현 예정
        }
    }

    private class YellowSkill : Skill() {
        override val name = "<bold>황혼"
        override val description = listOf(
            "<gray>5초간 황혼의 시간대에 돌입한다."
        )
        override val cooldown = Int.MAX_VALUE

        override fun use() {
            // TODO: 스킬 효과 구현 예정
        }
    }

    private class PassiveOne : Passive() {
        override val name = "<bold>시계"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>전투 시작 후, 시간이 흐를 때마다 여명, 정오, 자정의 각각 다른 시간대에 진입한다.",
            "<gray>현재 시간대에 따라 스킬의 효과와 위력이 변한다."
        )
    }

    private class PassiveTwo : Passive() {
        override val name = "<bold>시간대"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>각 시간대에 따라 다른 효과를 얻는다.",
            "",
            "<gray>여명 <italic>(태양이 뜨기 전 새벽)</italic> - 이 시간 동안 6의 피해를 막는 보호막을 얻는다.",
            "<gray>정오 <italic>(태양이 모든 것을 비추는 낮)</italic> - 이 시간 동안 적에게 가하는 피해가 20% 증가하고, 기본 공격으로 입힌 최종 피해의 25% 만큼 추가 피해를 입힌다.",
            "<gray>자정 <italic>(태양이 저물고 어두운 밤)</italic> - 이 시간 동안 적에게 받는 피해가 10% 감소하고, 초당 체력을 1 회복한다.",
            "<gray>황혼 <italic>(새벽과 낮, 밤이 교차하는 황혼의 때)</italic> - 모든 시간대의 효과를 적용한다."
        )
    }
}
