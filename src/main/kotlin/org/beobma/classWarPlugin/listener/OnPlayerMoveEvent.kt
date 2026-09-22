package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ability.AbilityTree

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.info.Info
import org.beobma.classWarPlugin.manager.GameManager.trainingInstance
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.beobma.classWarPlugin.domain.DomainManager
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.game.CooperativeAction

class OnPlayerMoveEvent : Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleportForecast(event: PlayerTeleportEvent) {
        val data = org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer(event.player)
            ?.playerDatas?.filterIsInstance<PlayerData>()?.firstOrNull { it.player == event.player } ?: return
        if (!data.canDispatchClassHandlers()) return
        org.beobma.classWarPlugin.ability.AbilityForecast.line(data, event.from, event.to, independent = true)
    }

    @EventHandler
    fun onEntityMove(event: PlayerMoveEvent) {
        val player = event.player
        if (event is PlayerTeleportEvent) return
        val game = Info.game ?: trainingInstance.find { game -> game.playerDatas.any { playerData -> playerData.entity == player } } ?: return
        val playerData = game.playerDatas.find { playerData -> playerData.entity == player } as? PlayerData ?: return
        if (!playerData.entityStatus.canMove || !game.canPerform(playerData.uniqueId, CooperativeAction.MOVE)) {
            val from = event.from
            val to = event.to
            if (from.x != to.x || from.y != to.y || from.z != to.z) {
                event.isCancelled = true
                return
            }
        }
        if (!playerData.canDispatchClassHandlers()) return
        for (bound in AbilityTree.handlers(playerData.gameClasses, StatusPlayerMoveHandler::class.java)) {
            bound.call { it.onPlayerMove(event, playerData) }
            if (event.isCancelled) return
        }

        // 상태이상
        for (status in playerData.statusAbnormalitys.toList()) {
            if (status !is StatusPlayerMoveHandler) continue
            status.fromSource { status.onPlayerMove(event, playerData) }
        }
    }
}
