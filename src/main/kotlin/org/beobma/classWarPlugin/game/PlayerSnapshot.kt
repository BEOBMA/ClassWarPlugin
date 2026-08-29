package org.beobma.classWarPlugin.game

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect

/**
 * 경기 참가 전 플레이어 상태를 복원하기 위한 값 객체다.
 * 위치와 인벤토리 아이템은 캡처 시 복제되어 이후 Bukkit 객체 변경과 분리된다.
 */
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
        /** 현재 플레이어 상태를 방어적으로 복사해 스냅샷으로 만든다. */
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
