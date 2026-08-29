package org.beobma.classWarPlugin.game

/** 한 경기가 거치는 단방향 진행 단계다. */
enum class GamePhase {
    WAITING,
    CLASS_SELECTION,
    COUNTDOWN,
    SCATTERING,
    RUNNING,
    FINISHED,
}
