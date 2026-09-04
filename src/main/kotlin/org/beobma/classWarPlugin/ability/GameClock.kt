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
    private var remaining = delay.coerceAtLeast(1L)
    var complete = false
        private set
    fun advance(suspended: Boolean): Boolean {
        if (complete || suspended) return false
        if (--remaining > 0L) return false
        if (period == null) complete = true else remaining = period.coerceAtLeast(1L)
        return true
    }
}
