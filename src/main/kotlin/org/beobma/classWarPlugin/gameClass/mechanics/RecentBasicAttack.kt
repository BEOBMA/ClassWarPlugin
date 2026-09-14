package org.beobma.classWarPlugin.gameClass.mechanics

/** Uses combat time, so pauses cannot consume the attack window. */
internal class RecentBasicAttack(private val durationTicks: Long = 60L) {
    private var lastHit: Long? = null
    fun record(tick: Long) { lastHit = tick }
    fun reset() { lastHit = null }
    fun isActive(tick: Long): Boolean = lastHit?.let { tick >= it && tick - it < durationTicks } ?: false
}
