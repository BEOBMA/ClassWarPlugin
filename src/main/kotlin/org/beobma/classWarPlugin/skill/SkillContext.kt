package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.inventory.ItemStack
import kotlin.math.roundToInt

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

    fun multiplyCooldown(multiplier: Double) {
        require(multiplier >= 0.0) { "Cooldown multiplier must be non-negative." }
        cooldownMultiplier *= multiplier
        cooldownTicks = (baseCooldownTicks * cooldownMultiplier).roundToInt().coerceAtLeast(0)
    }

    fun setCooldownTicks(ticks: Int) {
        cooldownTicks = ticks.coerceAtLeast(0)
        cooldownMultiplier = if (baseCooldownTicks > 0) {
            cooldownTicks.toDouble() / baseCooldownTicks
        } else {
            0.0
        }
    }
}
