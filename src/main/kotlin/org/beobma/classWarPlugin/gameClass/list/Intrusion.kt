package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Intrusion : GameClass() {
    override val classId = "intrusion"
    override val name = "<gray>난입"
    override val rank = Rank.B
    override val classItemMaterial = Material.RABBIT_FOOT
    override var skills: List<Skill> = listOf(
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo()
    )

    private class Passive : BasePassive() {
        override val name = "<bold>난입의 본능"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>전투 상태인 적이 있을 때, 해당 적의 위치가 지도에 표시된다.",
            "<gray>만약, 해당 적의 체력이 최대 체력의 40% 미만이라면",
            "<gray>해당 적은 지도에 강조 표기되고, 해당 적이 있는 방향으로 향할 때 <gold><bold>이동 속도가 50% 증가</bold><gray>한다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>도파민"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>난입의 본능에 의해 강조 표기된 대상을 직접 처치했다면",
            "<gray>자신은 <green><bold>자신 최대 체력의 30% 만큼 체력을 회복</bold><gray>한다.",
            "<gray>이 효과는 50초의 재사용 대기 시간을 가진다."
        )
    }
}
