package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.info.Info
import org.beobma.classWarPlugin.manager.GameManager.trainingInstance
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.beobma.classWarPlugin.gameClass.list.AreaDevelopment
import org.bukkit.Particle
import org.bukkit.Sound

class OnPlayerMoveEvent : Listener {

    @EventHandler
    fun onEntityMove(event: PlayerMoveEvent) {
        val player = event.player
        if (event is PlayerTeleportEvent) {
            if (AreaDevelopment.shouldBlockTeleport(player.uniqueId, event.from, event.to)) {
                event.isCancelled = true
                player.spawnParticle(Particle.SMOKE, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.45, 0.3, 0.025)
                player.playSound(player.location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.45f, 1.6f)
            }
            return
        }
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
        if (!playerData.canDispatchClassHandlers()) return
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
