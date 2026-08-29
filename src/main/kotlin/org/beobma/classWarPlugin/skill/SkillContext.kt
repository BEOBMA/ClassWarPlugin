package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.inventory.ItemStack
import kotlin.math.roundToInt

/**
 * 한 번의 스킬 사용 요청을 이벤트 처리기와 스킬 구현 사이에서 공유한다.
 * 재사용 대기시간은 틱 단위이며 변경 결과는 실제 스킬 실행이 끝난 뒤 적용된다.
 */
class SkillContext(
    val playerData: PlayerData,
    val skill: Skill,
    val clickedItem: ItemStack,
    val baseCooldownTicks: Int,
) {
    var isCancelled: Boolean = false
    var cooldownMultiplier: Double = 1.0
        private set
    var cooldownTicks: Int = baseCooldownTicks
        private set

    val isToggle: Boolean
        get() = skill.isOnOffSKill

    /**
     * 기본 재사용 대기시간에 누적 배율을 적용한다.
     *
     * @throws IllegalArgumentException [multiplier]가 음수인 경우
     */
    fun multiplyCooldown(multiplier: Double) {
        require(multiplier >= 0.0) { "Cooldown multiplier must be non-negative." }
        cooldownMultiplier *= multiplier
        cooldownTicks = (baseCooldownTicks * cooldownMultiplier).roundToInt().coerceAtLeast(0)
    }

    /** 최종 재사용 대기시간을 틱 단위로 덮어쓴다. 음수는 `0`으로 처리한다. */
    fun setCooldownTicks(ticks: Int) {
        cooldownTicks = ticks.coerceAtLeast(0)
        cooldownMultiplier = if (baseCooldownTicks > 0) {
            cooldownTicks.toDouble() / baseCooldownTicks
        } else {
            0.0
        }
    }
}
