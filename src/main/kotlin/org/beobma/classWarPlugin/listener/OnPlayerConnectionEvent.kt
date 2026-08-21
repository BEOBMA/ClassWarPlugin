package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.GameManager.handleReconnect
import org.beobma.classWarPlugin.manager.GameManager.handleTemporaryDisconnect
import org.beobma.classWarPlugin.manager.GameManager.stopTraining
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class OnPlayerConnectionEvent : Listener {
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (PlayerTagManager.hasTag(event.player, "isTraining")) {
            event.player.stopTraining()
        }
        handleTemporaryDisconnect(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        handleReconnect(event.player)
    }
}
