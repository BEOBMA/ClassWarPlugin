package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.ceil

object CooldownManager {
    private const val NANOS_PER_TICK = 50_000_000L

    private data class CooldownKey(val playerId: UUID, val skillId: String)
    private data class CooldownEntry(
        val expiresAtNanos: Long,
        val material: Material,
        val pausedAtNanos: Long? = null,
    )

    private val cooldowns: MutableMap<CooldownKey, CooldownEntry> = mutableMapOf()

    fun hasCooldown(player: Player, skill: Skill): Boolean = remainingTicks(player, skill) > 0

    fun remainingTicks(player: Player, skill: Skill): Int {
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return 0
        val remaining = remainingTicks(entry)
        if (remaining <= 0) cooldowns.remove(key)
        return remaining
    }

    fun setCooldown(player: Player, skill: Skill, item: ItemStack, ticks: Int) {
        val key = CooldownKey(player.uniqueId, skill.id)
        if (ticks <= 0) {
            val material = cooldowns.remove(key)?.material ?: item.type
            refreshMaterialCooldown(player, material)
            return
        }
        cooldowns[key] = CooldownEntry(System.nanoTime() + ticks * NANOS_PER_TICK, item.type)
        refreshMaterialCooldown(player, item.type)
    }

    fun resetCooldown(player: Player, skill: Skill) {
        val key = CooldownKey(player.uniqueId, skill.id)
        val material = cooldowns.remove(key)?.material ?: return
        refreshMaterialCooldown(player, material)
    }

    fun reduceCooldown(player: Player, skill: Skill, ticks: Int) {
        if (ticks <= 0) return
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        cooldowns[key] = entry.copy(expiresAtNanos = entry.expiresAtNanos - ticks * NANOS_PER_TICK)
        refreshMaterialCooldown(player, entry.material)
    }

    fun multiplyCooldown(player: Player, skill: Skill, multiplier: Double) {
        if (multiplier <= 0.0) return
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        val now = entry.pausedAtNanos ?: System.nanoTime()
        val remainingNanos = (entry.expiresAtNanos - now).coerceAtLeast(0L)
        val multiplied = (remainingNanos.toDouble() * multiplier).toLong()
        cooldowns[key] = entry.copy(expiresAtNanos = now + multiplied)
        refreshMaterialCooldown(player, entry.material)
    }

    fun pauseCooldown(player: Player, skill: Skill) {
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        if (entry.pausedAtNanos != null) return
        cooldowns[key] = entry.copy(pausedAtNanos = System.nanoTime())
        refreshMaterialCooldown(player, entry.material)
    }

    fun resumeCooldown(player: Player, skill: Skill) {
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        val pausedAt = entry.pausedAtNanos ?: return
        cooldowns[key] = entry.copy(
            expiresAtNanos = entry.expiresAtNanos + (System.nanoTime() - pausedAt),
            pausedAtNanos = null,
        )
        refreshMaterialCooldown(player, entry.material)
    }

    fun clear(playerIds: Collection<UUID>) {
        if (playerIds.isEmpty()) return
        cooldowns.keys.removeIf { it.playerId in playerIds }
    }

    fun refreshPlayer(player: Player) {
        cooldowns.asSequence()
            .filter { (key, _) -> key.playerId == player.uniqueId }
            .map { (_, entry) -> entry.material }
            .distinct()
            .forEach { material -> refreshMaterialCooldown(player, material) }
    }

    private fun refreshMaterialCooldown(player: Player, material: Material) {
        val maximumRemaining = cooldowns.asSequence()
            .filter { (key, entry) -> key.playerId == player.uniqueId && entry.material == material }
            .maxOfOrNull { (_, entry) -> remainingTicks(entry) }
            ?.coerceAtLeast(0)
            ?: 0
        player.setCooldown(material, maximumRemaining)
    }

    private fun remainingTicks(entry: CooldownEntry): Int {
        val now = entry.pausedAtNanos ?: System.nanoTime()
        return ceil((entry.expiresAtNanos - now).toDouble() / NANOS_PER_TICK).toInt().coerceAtLeast(0)
    }
}
