package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object CombatManager {
    private const val COMBAT_TIMEOUT_TICKS = 20L * 20L
    private val lastCombatTickByPlayer: MutableMap<UUID, Long> = mutableMapOf()

    fun recordSuccessfulDamage(context: DamageContext) {
        mark(context.attacker.uniqueId)
        (context.target as? PlayerData)?.let { mark(it.uniqueId) }
    }

    fun recordDamageTaken(playerData: PlayerData) {
        mark(playerData.uniqueId)
    }

    fun blocksNaturalRegeneration(player: Player): Boolean {
        val lastCombatTick = lastCombatTickByPlayer[player.uniqueId] ?: return false
        if (currentTick() - lastCombatTick < COMBAT_TIMEOUT_TICKS) return true
        lastCombatTickByPlayer.remove(player.uniqueId)
        return false
    }

    fun clear(playerIds: Collection<UUID>) {
        playerIds.forEach(lastCombatTickByPlayer::remove)
    }

    private fun mark(playerId: UUID) {
        lastCombatTickByPlayer[playerId] = currentTick()
    }

    private fun currentTick(): Long = Bukkit.getCurrentTick().toLong()
}
