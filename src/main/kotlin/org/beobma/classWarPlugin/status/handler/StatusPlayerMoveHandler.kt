package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.event.player.PlayerMoveEvent

/** 상태 보유 플레이어의 이동 이벤트를 받는 처리기다. */
interface StatusPlayerMoveHandler {
    fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData)
}
