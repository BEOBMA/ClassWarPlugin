package org.beobma.classWarPlugin.gameClass.handler

/** 플레이어 태스크가 정리되기 전에 활성 폼과 엔티티를 해제하는 훅. */
interface PlayerDeathHandler {
    fun onPlayerDeath()
}
