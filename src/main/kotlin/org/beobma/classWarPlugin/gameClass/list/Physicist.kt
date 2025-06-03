package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material

class Physicist : GameClass() {
    override val name = "<gray>물리학자"
    override val description = listOf(
        "<gold>역할군",
        "",
        "<gray>클래스 설명"
    )
    override val classItemMaterial = Material.APPLE
    override val weapon = PhysicistsTool()

    override var skills: List<Skill> = listOf(
        PhysicistsRedSkill(),
        PhysicistsOrangeSkill(),
        PhysicistsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        PhysicistsPassive()
    )
}

class PhysicistsTool : Weapon() {
    override val name = "<gray>무기 이름"
    override val description = listOf("<gray>무기 설명")
    override val material = Material.WOODEN_SWORD
}

class PhysicistsRedSkill : Skill() {
    override val name = "<blue><bold>광전 효과"
    override val description = listOf(
        "<gray>물리학자의 현재 상태에 따라 이하의 효과가 적용된다.",
        "",
        "<gray>입자 상태 - 광자를 발사하여 적중한 적에게 ?의 피해를 입힌다.",
        "<gray>파동 상태 - 주위 모든 적에게 ?의 피해를 입힌다.",
        "<gray>동시 상태 - 바라보는 방향으로 쌍전자를 발사하여 적중한 적에게 ?의 피해를 입힌다.",
        "<gray> 이후 전자를 폭파시켜 적중한 모든 적에게 ?의 피해를 추가로 입힌다.",
        "",
        "<gray>이후 동시 상태가 아니였다면 반대 상태가 된다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 상태에 따라 광전 효과 처리
        return true
    }
}

class PhysicistsOrangeSkill : Skill() {
    override val name = "<gold><bold>양자 중첩"
    override val description = listOf(
        "<gray>?초간 입자와 파동 상태를 중첩하여 동시 상태가 된다.",
        "<gray>이 시간동안 스킬 광전 효과의 재사용 대기 시간이 감소한다."
    )
    override val cooldown = 10

    override fun use(): Boolean {
        // TODO: 동시 상태 진입 및 쿨타임 감소 로직
        return true
    }
}

class PhysicistsYellowSkill : Skill() {
    override val name = "<yellow><bold>슈뢰딩거의 상자"
    override val description = listOf(
        "<gray>바라보는 적에게 ??의 피해를 입힌다."
    )
    override val cooldown = Int.MAX_VALUE

    override fun use(): Boolean {
        // TODO: 대상에게 무작위 피해 적용
        return true
    }
}

class PhysicistsPassive : Passive() {
    override val name = "<yellow><bold>관측"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>모든 스킬 계수가 무작위 숫자로 설정된다.",
        "<gray>최대치는 '?'의 개수에 따라 다르다."
    )
}
