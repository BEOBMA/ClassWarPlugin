package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Mathematician : GameClass() {
    override val name = "<gray>수학자"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.SHULKER_SHELL
    override val weapon = MathematiciansTool()

    override var skills: List<Skill> = listOf(
        MathematiciansRedSkill(),
        MathematiciansOrangeSkill(),
        MathematiciansYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        MathematiciansPassive()
    )
}

class MathematiciansTool : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class MathematiciansRedSkill : Skill() {
    override val name = "<blue><bold>좌표축 설정"
    override val description = listOf(
        "<gray>바라보는 블럭에 좌표축을 설정한다.",
        "<gray>좌표축은 최대 2개까지 존재할 수 있다.",
        "",
        "<dark_gray>좌표축 설치 시 좌표축 사이를 선분으로 이어 직육면체로 만들었을 때, 부피가 125를 초과하게 설치할 수는 없다."
    )
    override val cooldown = 0

    override fun use(): Boolean {
        // TODO: 좌표축 위치 저장 및 제한 로직
        return true
    }
}

class MathematiciansOrangeSkill : Skill() {
    override val name = "<gold><bold>직육면체"
    override val description = listOf(
        "<gray>좌표축 사이를 이어 직육면체로 만든다.",
        "<gray>직육면체 내부에 존재하는 모든 적에게 <gold><bold>(직육면체의 부피 / 5 만큼의 피해)</bold><gray>를 입힌다. (최대 25)",
        "<gray>스킬 사용 후에도 직육면체는 잔존한다."
    )
    override val cooldown = 5

    override fun use(): Boolean {
        // TODO: 좌표축 2개로 박스 생성 및 내부 적 타격
        return true
    }
}

class MathematiciansYellowSkill : Skill() {
    override val name = "<yellow><bold>전개"
    override val description = listOf(
        "<gray>모든 직육면체를 전개하고 제거한다.",
        "<gray>직육면체 내부에 존재하던 적에게 <gold><bold>(모든 직육면체의 개수 x 3)</bold><gray>의 피해를 입힌다. (최대 30)"
    )
    override val cooldown = 20

    override fun use(): Boolean {
        // TODO: 생성된 직육면체 삭제 및 피해 적용
        return true
    }
}

class MathematiciansPassive : Passive() {
    override val name = "<bold>난제"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>생성된 직육면체가 겹쳐지면 모든 좌표축을 제거하고 그 두 직육면체를 제거한다."
    )
}
