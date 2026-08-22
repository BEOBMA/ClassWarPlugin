package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.BattleMapManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent

class OnBattleMapEvent : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onMapDrop(event: PlayerDropItemEvent) {
        if (BattleMapManager.isBattleMap(event.itemDrop.itemStack)) {
            event.isCancelled = true
        }
    }
}
