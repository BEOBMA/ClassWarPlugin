package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ability.AbilityTree

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.handler.MovementInputHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInputEvent

class OnPlayerInputEvent : Listener {
    @EventHandler
    fun onPlayerInput(event: PlayerInputEvent) {
        val player = event.player
        if (!isGaming() && !PlayerTagManager.isTraining(player)) return
        val playerData = findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.find { it.uniqueId == player.uniqueId } ?: return
        if (!playerData.canDispatchClassHandlers()) return
        AbilityTree.handlers(playerData.gameClasses, MovementInputHandler::class.java)
            .forEach { bound -> bound.call { it.onPlayerInput(event) } }
        playerData.statusAbnormalitys.toList()
            .filterIsInstance<MovementInputHandler>()
            .forEach { handler ->
                (handler as org.beobma.classWarPlugin.status.StatusAbnormality).fromSource { handler.onPlayerInput(event) }
            }
    }
}
