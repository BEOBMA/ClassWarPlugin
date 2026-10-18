package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val CARTOONIST_SKILL_COOLDOWN_SECONDS = 40

class Cartoonist : GameClass() {
    private val runtime by lazy { org.beobma.classWarPlugin.gameClass.art.CartoonRuntime(abilityScope) }
    override val classId = "cartoonist"
    override val name = "<gray>만화가"
    override val rank = Rank.S
    override val classItemMaterial = Material.BOOK
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
    )

    private inner class RedSkill : Skill() {
        override val definitionId = "cartoonist/rewind"
        override val name = "<bold>역재생"
        override val description = listOf(
            "<gray>자신과 자신 주변 6칸 이내의 모든 적은 3초간 어떤 행동이든 할 때마다",
            "<gray>자신의 위치에 자신의 현재 상태를 모두 반영한 분신을 남긴다. (대상 당 10틱마다)",
            "",
            "<gray>지속 시간 종료 후 이 효과에 영향을 받은 모든 플레이어는",
            "<gray>지속 시간 동안 자신이 했던 행동을 역재생으로 따라하며 되돌아간다.",
            "<gray>되돌아가는 도중에는 어떠한 행동도 할 수 없는 상태가 된다.",
            "<gray>되돌아가며 그 시점에 사용했던 기본 공격, 스킬, 행동 또한 되돌아간다.",
            "<gray>되돌아가며 분신을 남겼던 플레이어는 그 시점에 도달할 때마다 2의 피해를 입는다.",
            "",
            "<dark_gray>분신은 떨어지거나 밀쳐지지도, 피해를 입지도 않은 채 고정된다.",
            "<dark_gray>위치나 스킬, 재사용 대기 시간, 행동은 되돌릴 수 있지만, 체력은 되돌릴 수 없다.",
            "<dark_gray>이 스킬 지속 도중, 배속 재생 스킬은 사용할 수 없다."
        )
        override val cooldown = CARTOONIST_SKILL_COOLDOWN_SECONDS
        override fun isUseSuccess(): Boolean = runtime.canStart()

        override fun use(): Boolean {
            return runtime.start(false)
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "cartoonist/fast-forward"
        override val name = "<bold>배속 재생"
        override val description = listOf(
            "<gray>자신 주변 16칸 이내의 모든 적은 3초간 {keyword:Distortion} 상태가 된다.",
            "<gray>자신은 지속 시간 동안 5틱마다 자신의 현재 상태를 모두 반영한 분신을 남긴다.",
            "<gray>기록 중 기본 공격과 스킬은 사용 입력만 기록하고, 피해와 실제 효과는 발생하지 않는다.",
            "",
            "<gray>지속 시간 종료 후, 자신은 스킬 사용 시점의 위치와 상태로 돌아온다.",
            "<gray>이후 자신이 했던 행동을 배속 재생하여 2배의 속도로 따라간다.",
            "<gray>따라가는 도중에는 어떠한 행동도 할 수 없는 상태가 된다.",
            "<gray>따라가며 그 시점에 사용했던 기본 공격, 스킬, 행동 또한 그대로 따라한다.",
            "<gray>기록했던 기본 공격과 스킬의 실제 효과는 이때 발동한다.",
            "",
            "<dark_gray>분신은 떨어지거나 밀쳐지지도, 피해를 입지도 않은 채 고정된다.",
            "<dark_gray>위치나 스킬, 재사용 대기 시간, 행동은 되돌릴 수 있지만, 체력은 되돌릴 수 없다.",
            "<dark_gray>이 스킬 지속 도중, 역재생 스킬은 사용할 수 없다."
        )
        override val cooldown = CARTOONIST_SKILL_COOLDOWN_SECONDS
        override fun isUseSuccess(): Boolean = runtime.canStart()

        override fun use(): Boolean {
            return runtime.start(true)
        }
    }
}
