package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.info.Info.isGaming
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent

class OnPlayerItemHeldEvent : Listener {

    @EventHandler
    fun onHotbarChange(event: PlayerItemHeldEvent) {
        val next = event.newSlot

        if (!isGaming()) return
        if (next == 0) return
        val player = event.player
        val playerData = game?.playerDatas?.find { it.player == player } ?: return
        val gameClass = playerData.gameClass ?: return
        gameClass.skills.getOrNull(next - 1)?.use()
        event.isCancelled = true
    }
}