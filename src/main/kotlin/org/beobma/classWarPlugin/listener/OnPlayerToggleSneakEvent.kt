package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.handler.SneakInputHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent

class OnPlayerToggleSneakEvent : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (!isGaming() && !PlayerTagManager.isTraining(player)) return
        val playerData = findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.find { it.uniqueId == player.uniqueId } ?: return
        if (!playerData.canDispatchClassHandlers()) return
        for (gameClass in playerData.gameClasses) {
            if (gameClass !is SneakInputHandler) continue
            gameClass.onPlayerToggleSneak(event)
            if (event.isCancelled) return
        }
        playerData.statusAbnormalitys.toList()
            .filterIsInstance<SneakInputHandler>()
            .forEach { status ->
                status.onPlayerToggleSneak(event)
                if (event.isCancelled) return
            }
    }
}
