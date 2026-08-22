package org.beobma.classWarPlugin.gameClass.handler

import org.bukkit.event.player.PlayerToggleSneakEvent

/** 웅크리기 상태가 바뀌는 순간 별도 동작을 수행하는 클래스가 구현하는 훅. */
interface SneakInputHandler {
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {}
}
