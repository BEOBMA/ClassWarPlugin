package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.UtilManager.setPlayerMaxHealth
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class OnPlayerDeathEvent : Listener{

    @EventHandler
    fun onPlayerDeathEvent(event: PlayerDeathEvent) {
        val damager = event.player
        val entity = event.entity
        val game = game ?: return
        val damagerData = game.playerDatas.find { it.player == damager } ?: return
        val entityData = game.playerDatas.find { it.player == entity } ?: return
        val entityBukkitTasks = entityData.bukkitTasks
        entityBukkitTasks.forEach {
            it.cancel()
        }
        entityData.playerStatus.isDead = true
        entityData.bukkitTasks.clear()
        entity.setPlayerMaxHealth(40.0)
        entity.gameMode = GameMode.SPECTATOR
    }
}