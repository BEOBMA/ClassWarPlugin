package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.skill.Passive as BasePassive


class Archaeologist : GameClass() {
    override val classId = "archaeologist"
    override val name = "<gray>고고학자"
    override val rank = Rank.A
    override val classItemMaterial = Material.BRUSH
    override var skills: List<Skill> = listOf(
    )


    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private class ChestSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>보물"
        override val description = listOf(
            "<gray>사용 시 완전히 소멸하는 휘발성 스킬",
            "",
            "<gray>무작위 클래스의 스킬, 패시브, 무기 중 하나를 얻는다."
        )
        override val cooldown = 0

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>발굴 구역"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 혹은 발굴 완료 후 자신이 도달 가능한 청크 하나가 발굴 구역이 된다.",
            "<gray>자신은 발굴 구역의 위치를 지도를 통해 확인할 수 있다.",
            "",
            "<gray>발굴 구역 안에서 머무르면 발굴을 시작한다.",
            "<gray>발굴은 6초가 소모되며, 발굴 완료 시 보물을 얻고 구역은 제거된다."
        )
    }
}
