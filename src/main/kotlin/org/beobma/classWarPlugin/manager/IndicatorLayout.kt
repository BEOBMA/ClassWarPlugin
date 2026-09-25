package org.beobma.classWarPlugin.manager

/** Separate bounded lanes prevent frequent damage numbers from evicting activation labels. */
internal object IndicatorLayout {
    fun slots(priority: Int): IntRange = when (priority) {
        0 -> 0..1
        1 -> 2..4
        else -> 5..7
    }
    fun x(slot: Int): Float = when (slot) { in 0..1 -> -2.0f; in 2..4 -> 0f; else -> 2.2f }
    fun y(slot: Int): Float = when (slot) { in 0..1 -> slot; in 2..4 -> slot-2; else -> slot-5 } * 1.8f
    fun eviction(priorities: List<Int>, incoming: Int): Int? = priorities.indices
        .filter { priorities[it] <= incoming }.minByOrNull { priorities[it] }
}
