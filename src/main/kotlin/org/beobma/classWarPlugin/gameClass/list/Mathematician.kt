package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Mathematician : GameClass() {
    override val name = "<gray>기하학자"
    override val rank = Rank.A
    override val classItemMaterial = Material.SHULKER_SHELL

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf()

    private class RedSkill : Skill() {
        override val name = "<bold>좌표 설정"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 블럭에 좌표를 지정한다.",
            "",
            "<gray>첫 번째 좌표와 두 번째 좌표가 지정되면",
            "<gray>두 좌표를 꼭짓점으로 하는 직육면체가 생성된다.",
            "",
            "<gray>직육면체의 부피는 최대 125이며",
            "<gray>각 변의 길이는 8칸을 초과할 수 없다.",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 모든 좌표를 제거한다."
        )
        override val cooldown = 1

        override fun use() {
            // TODO: 좌표축 위치 저장 및 제한 로직
        }
    }

    private class OrangeSkill : Skill() {
        override val name = "<bold>압축"
        override val description = listOf(
            "<gray>직육면체를 중심으로 압축한다.",
            "<gray>내부의 모든 적에게 직육면체의 부피에 반비례하여 피해를 입힌다.",
            "<gray>이후 직육면체와 좌표가 모두 제거된다.",
            "",
            "<dark_gray>피해량은 최대 (5)와 (16 - √부피) 중 큰 값으로 결정된다.",
            "<dark_gray>"
        )
        override val cooldown = 18

        override fun use() {
        }
    }
}
