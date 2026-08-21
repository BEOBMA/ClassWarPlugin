package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class TimeManiqulator : GameClass() {
    override val name = "<gray>시간 조작자"
    override val rank = Rank.C
    override val classItemMaterial = Material.CLOCK
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )


    private class Weapon : BaseWeapon() {
        override val name = "<gray>대인용 지팡이"
        override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
        override val material = Material.WOODEN_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<blue><bold>체크포인트"
        override val description = listOf(
            "<gray>사용 시 현재 자신의 상태를 체크포인트로 저장한다.",
            "<gray>이미 저장된 체크포인트가 있다면 제거하고 저장한다.",
            "<gray>저장 가능한 상태는 현재 체력, 위치만 해당된다."
        )
        override val cooldown = 0

        override fun use() {
            //TODO()
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<gold><bold>불러오기"
        override val description = listOf(
            "<gray>저장된 체크포인트가 있을 때에만 사용할 수 있다.",
            "",
            "<gray>체크포인트를 불러온다.",
            "<gray>불러온 후 체크포인트 저장 시점과의 차이에 비례하여 <dark_red><bold>최대 체력이 감소</bold><gray>한다.",
            "",
            "<dark_gray>위치 차이 1칸 당 최대 체력이 0.1 감소한다.",
            "<dark_gray>체력 차이 1 당 0.5 감소한다."
        )
        override val cooldown = 0

        override fun use() {
            //TODO()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<yellow><bold>생명 교차"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>사망 시 {keyword:Invalidity}로 하고 체크포인트를 불러온다.",
            "<gray>불러온 후 <dark_red><bold>최대 체력이 기본 최대 체력의 40% 만큼 감소</bold><gray>한다."
        )
    }
}
