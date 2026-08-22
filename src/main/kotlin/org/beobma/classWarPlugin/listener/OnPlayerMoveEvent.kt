package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.info.Info
import org.beobma.classWarPlugin.manager.GameManager.trainingInstance
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent

class OnPlayerMoveEvent : Listener {

    @EventHandler
    fun onEntityMove(event: PlayerMoveEvent) {
        if (event is PlayerTeleportEvent) return
        val player = event.player
        val game = Info.game ?: trainingInstance.find { game -> game.playerDatas.any { playerData -> playerData.entity == player } } ?: return
        val playerData = game.playerDatas.find { playerData -> playerData.entity == player } as? PlayerData ?: return
        if (!playerData.entityStatus.canMove) {
            val from = event.from
            val to = event.to
            if (from.x != to.x || from.y != to.y || from.z != to.z) {
                event.isCancelled = true
                return
            }
        }
        val gameClass = playerData.gameClass ?: return

        if (gameClass is StatusPlayerMoveHandler) {
            gameClass.onPlayerMove(event, playerData)
            if (event.isCancelled) return
        }

        // 상태이상
        for (status in playerData.statusAbnormalitys) {
            if (status !is StatusPlayerMoveHandler) continue
            status.onPlayerMove(event, playerData)
        }
    }
}
