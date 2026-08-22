package org.beobma.classWarPlugin.gameClass.handler

import org.bukkit.event.player.PlayerInputEvent

/** 플레이어의 실제 이동 입력이 바뀌는 순간 별도 동작을 수행하는 클래스가 구현하는 훅. */
interface MovementInputHandler {
    fun onPlayerInput(event: PlayerInputEvent) {}
}
