package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class TimeManiqulator : GameClass() {
    override val name = "<gray>시간 조작자"
    override val description = listOf(
        "<gold>다재다능",
        "",
        "<gray>시간 조작자는 여러 방면에서 활용도가 높으며, 시간을 조작하는 강력한 스킬을 사용합니다."
    )
    override val classItemMaterial = Material.CLOCK
    override val weapon = TimeManiqulatorsStaff()

    override var skills: List<Skill> = listOf(
        TimeManiqulatorsRedSkill(),
        TimeManiqulatorsOrangeSkill()
    )

    override var passives: List<Passive> = listOf(
        TimeManiqulatorsPassive()
    )
}

class TimeManiqulatorsStaff : Weapon() {
    override val name = "<gray>대인용 지팡이"
    override val description = listOf("<gray>검처럼 사용할 수 있는 지팡이.")
    override val material = Material.WOODEN_SWORD
}

class TimeManiqulatorsRedSkill() : Skill() {
    override val name = "<blue><bold>체크포인트"
    override val description = listOf(
        "<gray>사용 시 현재 자신의 상태를 체크포인트로 저장한다.",
        "<gray>이미 저장된 체크포인트가 있다면 제거하고 저장한다.",
        "<gray>저장 가능한 상태는 현재 체력, 위치만 해당된다."
    )
    override val cooldown = 0

    override fun use(): Boolean {
        return true
    }
}

class TimeManiqulatorsOrangeSkill() : Skill() {
    override val name = "<gold><bold>불러오기"
    override val description = listOf(
        "<gray>저장된 체크포인트가 있을 때에만 사용할 수 있다.",
        "",
        "<gray>체크포인트를 불러온다.",
        "<gray>불러온 후 체크포인트 저장 시점과의 차이에 비례하여 <dark_red><bold>최대 체력이 감소</bold><gray>한다.",
        "",
        "<dark_gray>위치 차이 1칸 당 최대 체력이 0.1 감소하고.",
        "<dark_gray>체력 차이 1 당 0.5 감소한다."
    )
    override val cooldown = 0

    override fun use(): Boolean {
        return true
    }
}

class TimeManiqulatorsPassive() : Passive() {
    override val name = "<yellow><bold>생명 교차"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>사망 시 ${Keyword.Invalidity.string}로 하고 체크포인트를 불러온다.",
        "<gray>불러온 후 <dark_red><bold>최대 체력이 기본 최대 체력의 40% 만큼 감소</bold><gray>한다."
    )
}
