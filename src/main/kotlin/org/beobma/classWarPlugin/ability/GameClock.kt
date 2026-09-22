package org.beobma.classWarPlugin.ability

/** Monotonic combat time; suspension does not consume effect durations. */
class GameClock(private val ticks: () -> Long) {
    private var last = ticks()
    private var elapsed = 0L
    var paused = false
        set(value) { now(); field = value }

    fun now(): Long {
        val current = ticks()
        val delta = (current - last).and(0xffffffffL)
        last = current
        if (!paused) elapsed += delta
        return elapsed
    }
}

/** The same timer is used for scheduled effects and deterministic tests. */
class EffectTimer(delay: Long, private val period: Long?) {
    private var remaining = delay.coerceAtLeast(1L).toDouble()
    var complete = false
        private set
    fun advance(suspended: Boolean, timeScale: Double = 1.0): Boolean {
        if (complete || suspended) return false
        require(timeScale.isFinite() && timeScale in 0.0..1.0)
        remaining -= timeScale
        if (remaining > 0.0000001) return false
        if (period == null) complete = true else remaining = period.coerceAtLeast(1L).toDouble()
        return true
    }
}
