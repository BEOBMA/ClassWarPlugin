package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.event.player.PlayerMoveEvent

interface StatusPlayerMoveHandler {
    fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData)
}