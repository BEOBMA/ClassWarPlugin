package org.beobma.classWarPlugin.gameClass.metronome

import kotlin.math.abs

/** Hit judgement uses the audible transport frame, including its tempo and musical phase. */
internal class RhythmState {
    var streak = 0
        private set
    var subdivision = 1
        private set
    private var lastSlot: Long? = null
    private var lastEpoch: Long? = null
    private var attacksPerSecond = 1.0
    private var firstTick = 0L
    private var lastHitTick: Long? = null
    private val intervals = ArrayDeque<Long>()
    val stage: Int get() = minOf(
        (streak / 8).coerceAtMost(MetronomeScore.count),
        when { attacksPerSecond <= 1.6 -> 4; attacksPerSecond <= 3.2 -> 10; attacksPerSecond <= 5.0 -> 16; else -> MetronomeScore.count },
    )
    val damageMultiplier: Double get() {
        val heldSeconds = ((lastHitTick ?: firstTick) - firstTick).coerceAtLeast(0) / 20.0
        return 1.0 + (streak * 0.015 + heldSeconds * 0.005).coerceAtMost(1.5)
    }

    fun reset() {
        streak = 0
        subdivision = 1
        lastSlot = null
        lastEpoch = null
        attacksPerSecond = 1.0
        firstTick = 0
        lastHitTick = null
        intervals.clear()
    }

    fun expire(tick: Long): Boolean {
        if (lastHitTick?.let { tick - it > 40 } != true) return false
        reset()
        return true
    }

    /** The same rounded musical grid triggers both the note and its valid attack tick. */
    fun attack(tick: Long, timing: RhythmTiming = RhythmTiming(0, tick, 60)): Boolean {
        expire(tick)
        val slot = timing.slot
        if (!timing.isOnGrid || (lastEpoch == timing.epoch && lastSlot?.let { slot <= it } == true)) {
            // Keep the consumed slot across a miss so repeated packets cannot reopen it.
            val consumed = lastSlot
            val consumedEpoch = lastEpoch
            reset()
            lastSlot = consumed
            lastEpoch = consumedEpoch
            return false
        }
        val previous = lastHitTick
        if (previous == null) firstTick = tick
        else {
            intervals.addLast(tick - previous)
            if (intervals.size > 5) intervals.removeFirst()
            if (intervals.size >= 3) {
                val median = intervals.sorted()[intervals.size / 2]
                attacksPerSecond = 20.0 / median.coerceAtLeast(1L)
                subdivision = listOf(1, 2, 4, 8).filter { it <= timing.subdivisions }
                    .minBy { abs(1200.0 / (timing.bpm * it) - median) }
            }
        }
        lastSlot = slot
        lastEpoch = timing.epoch
        lastHitTick = tick
        streak++
        return true
    }
}
