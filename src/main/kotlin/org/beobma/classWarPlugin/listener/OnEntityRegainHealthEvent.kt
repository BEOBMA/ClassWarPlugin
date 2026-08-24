package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.CombatManager
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.Reverse
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent

class OnEntityRegainHealthEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        val entityPlayer = event.entity as? Player ?: return
        val playerData = findGameForPlayer(entityPlayer)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.find { it.uniqueId == entityPlayer.uniqueId }
        if (playerData != null && Reverse.invertHealingIfNeeded(playerData, event.amount)) {
            event.isCancelled = true
            return
        }
        if (event.regainReason != EntityRegainHealthEvent.RegainReason.SATIATED &&
            event.regainReason != EntityRegainHealthEvent.RegainReason.REGEN
        ) return

        if (CombatManager.blocksNaturalRegeneration(entityPlayer)) event.isCancelled = true
    }
}
