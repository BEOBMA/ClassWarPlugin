package org.beobma.classWarPlugin.gameClass.pioneer

import java.util.UUID

/** Combat ticks only. Hits maintain acceleration; at most one stack is awarded per six seconds. */
internal class PioneerState {
    var foresight = 30
    var bullets = 0
        private set
    var acceleration = 0
        private set
    private var target: UUID? = null
    private var lastHit = Long.MIN_VALUE / 2
    private var lastGain = Long.MIN_VALUE / 2
    var chainStage = 0
        private set
    var chainExpires = 0L
        private set

    fun hit(id: UUID, tick: Long) {
        expire(tick)
        if (target != id) { acceleration = 0; target = id; lastGain = Long.MIN_VALUE / 2 }
        lastHit = tick
        if (tick - lastGain >= 120) { acceleration = (acceleration + 1).coerceAtMost(5); lastGain = tick }
    }
    fun expire(tick: Long) {
        if (tick - lastHit >= 80) { acceleration = 0; target = null }
    }
    fun addBullets(amount: Int) { bullets = (bullets + amount).coerceIn(0, 6) }
    fun spendBullets(amount: Int): Boolean {
        if (bullets < amount) return false
        bullets -= amount
        return true
    }
    fun advanceChain(tick: Long): Int {
        if (chainStage == 0) chainExpires = tick + 200
        if (tick >= chainExpires || chainStage >= 5) return 0
        return ++chainStage
    }
    fun endChain() { chainStage = 0; chainExpires = 0 }
}
