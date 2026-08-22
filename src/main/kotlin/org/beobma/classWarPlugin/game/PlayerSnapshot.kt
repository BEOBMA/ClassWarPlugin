package org.beobma.classWarPlugin.game

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect

data class PlayerSnapshot(
    val location: Location,
    val inventoryContents: Array<ItemStack?>,
    val gameMode: GameMode,
    val health: Double,
    val foodLevel: Int,
    val saturation: Float,
    val exhaustion: Float,
    val level: Int,
    val experience: Float,
    val totalExperience: Int,
    val potionEffects: Collection<PotionEffect>,
    val fireTicks: Int,
    val allowFlight: Boolean,
    val isFlying: Boolean,
    val hasGravity: Boolean,
    val walkSpeed: Float,
    val flySpeed: Float,
    val movementSpeedBase: Double?,
    val attackSpeedBase: Double?,
    val maxHealthBase: Double?,
    val jumpStrengthBase: Double?,
    val scaleBase: Double?,
) {
    companion object {
        fun capture(player: Player): PlayerSnapshot = PlayerSnapshot(
            location = player.location.clone(),
            inventoryContents = player.inventory.contents.map { it?.clone() }.toTypedArray(),
            gameMode = player.gameMode,
            health = player.health,
            foodLevel = player.foodLevel,
            saturation = player.saturation,
            exhaustion = player.exhaustion,
            level = player.level,
            experience = player.exp,
            totalExperience = player.totalExperience,
            potionEffects = player.activePotionEffects.toList(),
            fireTicks = player.fireTicks,
            allowFlight = player.allowFlight,
            isFlying = player.isFlying,
            hasGravity = player.hasGravity(),
            walkSpeed = player.walkSpeed,
            flySpeed = player.flySpeed,
            movementSpeedBase = player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue,
            attackSpeedBase = player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue,
            maxHealthBase = player.getAttribute(Attribute.MAX_HEALTH)?.baseValue,
            jumpStrengthBase = player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue,
            scaleBase = player.getAttribute(Attribute.SCALE)?.baseValue,
        )
    }
}
