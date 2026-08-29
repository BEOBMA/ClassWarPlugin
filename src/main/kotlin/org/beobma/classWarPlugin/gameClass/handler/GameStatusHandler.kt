package org.beobma.classWarPlugin.gameClass.handler

/** 전투 시작과 경기 초 단위 경과 알림을 받는 클래스 구성요소다. */
interface GameStatusHandler {
    /** 클래스 선택과 산개가 끝나 실제 전투가 시작될 때 한 번 호출된다. */
    fun onBattleStart()

    /** 실행 중이며 일시 정지되지 않은 경기에서 1초마다 호출된다. */
    fun onGameTimePasses()
}
