package org.beobma.classWarPlugin.ability

/** Removing an effect recomputes from the current baseline, independent of removal order. */
class ScalarModifiers(var base: Double, private val minimum: Double = 0.0) {
    private val multipliers = linkedMapOf<Any, Double>()
    private val caps = linkedMapOf<Any, Double>()
    val value: Double get() = (base * multipliers.values.fold(1.0, Double::times))
        .coerceAtMost(caps.values.minOrNull() ?: Double.POSITIVE_INFINITY).coerceAtLeast(minimum)
    fun set(key: Any, multiplier: Double, maximum: Double = Double.POSITIVE_INFINITY) {
        require(multiplier.isFinite() && multiplier >= 0)
        multipliers[key] = multiplier
        caps[key] = maximum
    }
    fun remove(key: Any) { multipliers.remove(key); caps.remove(key) }
}
