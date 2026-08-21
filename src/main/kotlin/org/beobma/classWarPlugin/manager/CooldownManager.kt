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
    private data class CooldownEntry(val expiresAtNanos: Long, val material: Material)

    private val cooldowns: MutableMap<CooldownKey, CooldownEntry> = mutableMapOf()

    fun hasCooldown(player: Player, skill: Skill): Boolean = remainingTicks(player, skill) > 0

    fun remainingTicks(player: Player, skill: Skill): Int {
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return 0
        val remaining = ticksUntil(entry.expiresAtNanos)
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
            .maxOfOrNull { (_, entry) -> ticksUntil(entry.expiresAtNanos) }
            ?.coerceAtLeast(0)
            ?: 0
        player.setCooldown(material, maximumRemaining)
    }

    private fun ticksUntil(expiresAtNanos: Long): Int =
        ceil((expiresAtNanos - System.nanoTime()).toDouble() / NANOS_PER_TICK).toInt().coerceAtLeast(0)
}
