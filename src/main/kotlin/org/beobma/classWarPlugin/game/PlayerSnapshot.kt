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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerSnapshot

        if (health != other.health) return false
        if (foodLevel != other.foodLevel) return false
        if (saturation != other.saturation) return false
        if (exhaustion != other.exhaustion) return false
        if (level != other.level) return false
        if (experience != other.experience) return false
        if (totalExperience != other.totalExperience) return false
        if (fireTicks != other.fireTicks) return false
        if (allowFlight != other.allowFlight) return false
        if (isFlying != other.isFlying) return false
        if (hasGravity != other.hasGravity) return false
        if (walkSpeed != other.walkSpeed) return false
        if (flySpeed != other.flySpeed) return false
        if (movementSpeedBase != other.movementSpeedBase) return false
        if (attackSpeedBase != other.attackSpeedBase) return false
        if (maxHealthBase != other.maxHealthBase) return false
        if (jumpStrengthBase != other.jumpStrengthBase) return false
        if (scaleBase != other.scaleBase) return false
        if (location != other.location) return false
        if (!inventoryContents.contentEquals(other.inventoryContents)) return false
        if (gameMode != other.gameMode) return false
        if (potionEffects != other.potionEffects) return false

        return true
    }

    override fun hashCode(): Int {
        var result = health.hashCode()
        result = 31 * result + foodLevel
        result = 31 * result + saturation.hashCode()
        result = 31 * result + exhaustion.hashCode()
        result = 31 * result + level
        result = 31 * result + experience.hashCode()
        result = 31 * result + totalExperience
        result = 31 * result + fireTicks
        result = 31 * result + allowFlight.hashCode()
        result = 31 * result + isFlying.hashCode()
        result = 31 * result + hasGravity.hashCode()
        result = 31 * result + walkSpeed.hashCode()
        result = 31 * result + flySpeed.hashCode()
        result = 31 * result + (movementSpeedBase?.hashCode() ?: 0)
        result = 31 * result + (attackSpeedBase?.hashCode() ?: 0)
        result = 31 * result + (maxHealthBase?.hashCode() ?: 0)
        result = 31 * result + (jumpStrengthBase?.hashCode() ?: 0)
        result = 31 * result + (scaleBase?.hashCode() ?: 0)
        result = 31 * result + location.hashCode()
        result = 31 * result + inventoryContents.contentHashCode()
        result = 31 * result + gameMode.hashCode()
        result = 31 * result + potionEffects.hashCode()
        return result
    }
}
