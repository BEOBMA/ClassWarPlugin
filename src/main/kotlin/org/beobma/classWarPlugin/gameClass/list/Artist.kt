package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val DUMMY_RED_SKILL_COOLDOWN_SECONDS = 40

class Artist : GameClass() {
    override val classId = "artist"
    override val name = "<gray>화가"
    override val rank = Rank.S
    override val classItemMaterial = Material.BRUSH
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo()
    )


    private class RedSkill : Skill() {
        override val definitionId = "dummy/red-skill"
        override val name = "<bold>덧칠"
        override val description = listOf(
            "<gray>10초간 {keyword:Disability}, {keyword:Stealth} 상태가 된다.",
            "",
            "<gray>하늘 높은 곳으로 올라가 아래를 바라보는 방향으로 시점이 고정된다.",
            "<gray>마우스를 돌리면 시점 회전 대신, 마우스를 돌린 방향으로 이동한다.",
            "",
            "<gray>스킬 지속 중 자신 아래 3x3 범위 내에 있는 모든 적에게 초당 2의 피해를 입힌다.",
            "<gray>또한 자신 아래 3x3 범위 내에 있는 공기에 드러난 모든 블럭을 15초간 색체 블럭으로 만든다.",
            "",
            "<dark_gray>자신의 Y축 높이는 가장 높은 블럭보다 20칸 높은 곳으로 설정된다.",
            "<dark_gray>스킬 종료 후 자신은 원래 위치로 이동한다."
        )
        override val cooldown = DUMMY_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>색채 블럭"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시 대상 주변 3칸 범위의 블럭을 칠해 10초간 색채 블럭으로 만든다.",
            "",
            "<gray>자신은 색체 블럭에 닿은 상태에서 <gold><bold>이동 속도가 20% 증가</bold><gray>한다.",
            "<gray>색체 블럭에 닿은 적은 초당 색체 블럭 단계에 비례한 피해를 입는다. (최소 0.01, 최대 4)",
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>덧칠"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>색체 블럭 위에서 적이 피해를 입을 때마다 주변 색체 블럭의 단계가 증가한다.",
            "<gray>색체 단계가 증가할 수록, 색체 블럭은 알록달록한 색에서 붉은 색으로 변화한다."
        )
    }
}
