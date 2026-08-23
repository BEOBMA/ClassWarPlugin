package org.beobma.classWarPlugin.gameClass.handler

/** 게임 종료 직전 영구 월드/엔티티 변경을 정리하는 훅. */
interface GameEndHandler {
    fun onGameEnd()
}
