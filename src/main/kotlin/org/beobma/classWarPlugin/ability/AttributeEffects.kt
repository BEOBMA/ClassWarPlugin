package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.attribute.Attribute

/** Per-player attribute contributions use a fresh Player after reconnect. */
class AttributeEffects(private val data: PlayerData) {
    private val values = mutableMapOf<Attribute, ScalarModifiers>()
    private var walk: ScalarModifiers? = null
    private val growthRefreshers = mutableMapOf<Any, () -> Unit>()

    private fun ledger(attribute: Attribute) = values.getOrPut(attribute) {
        ScalarModifiers(data.player.getAttribute(attribute)?.baseValue ?: 1.0,
            if (attribute == Attribute.SCALE) 0.0625 else if (attribute == Attribute.MAX_HEALTH) 1.0 else 0.0)
    }

    /** Aggregated status effects share the same baseline as class contributions. */
    fun setContribution(key: Any, attribute: Attribute, multiplier: Double?) {
        if (multiplier == null && attribute !in values) return
        val value = ledger(attribute)
        if (multiplier == null) value.remove(key) else value.set(key, multiplier.coerceAtLeast(0.0))
        apply(attribute, value)
    }

    fun multiply(scope: AbilityScope, attribute: Attribute, multiplier: Double, maximum: Double = Double.POSITIVE_INFINITY): Lease {
        val value = ledger(attribute)
        val axis = when (attribute) {
            Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED -> org.beobma.classWarPlugin.growth.GrowthAxis.SPEED
            Attribute.MAX_HEALTH -> org.beobma.classWarPlugin.growth.GrowthAxis.SHIELD
            else -> null
        }
        return lease(scope, value, multiplier, maximum, transform = { raw ->
            if (axis == null || raw <= 1.0) raw else 1.0 + (raw - 1.0) *
                org.beobma.classWarPlugin.growth.GrowthScaling.multiplier(data, axis, scope.classId)
        }) { apply(attribute, value) }
    }

    fun walkSpeed(scope: AbilityScope, multiplier: Double): Lease {
        val value = walk ?: ScalarModifiers(data.player.walkSpeed.toDouble()).also { walk = it }
        return lease(scope, value, multiplier, transform = { raw ->
            if (raw <= 1.0) raw else 1.0 + (raw - 1.0) * org.beobma.classWarPlugin.growth.GrowthScaling.multiplier(
                data, org.beobma.classWarPlugin.growth.GrowthAxis.SPEED, scope.classId)
        }) { data.player.walkSpeed = value.value.coerceIn(-1.0, 1.0).toFloat() }
    }

    fun changeBase(attribute: Attribute, transform: (Double) -> Double) {
        val value = values[attribute]
        val baseline = value ?: ledger(attribute)
        baseline.base = transform(baseline.base)
        apply(attribute, baseline)
    }

    fun refresh() {
        growthRefreshers.values.forEach { it() }
        values.forEach { (attribute, value) -> apply(attribute, value) }
        walk?.let { data.player.walkSpeed = it.value.coerceIn(-1.0, 1.0).toFloat() }
    }

    private fun apply(attribute: Attribute, value: ScalarModifiers) {
        data.player.getAttribute(attribute)?.let { instance ->
            instance.baseValue = value.value
            if (attribute == Attribute.MAX_HEALTH && !data.player.isDead && data.player.health > 0.0) {
                data.player.health = data.player.health.coerceAtMost(instance.value)
            }
        }
    }

    private fun lease(scope: AbilityScope, value: ScalarModifiers, multiplier: Double,
                      maximum: Double = Double.POSITIVE_INFINITY, transform: (Double) -> Double = { it }, apply: () -> Unit): Lease {
        val key = Any()
        var raw = multiplier
        fun recalculate() { value.set(key, transform(raw), maximum) }
        growthRefreshers[key] = ::recalculate
        recalculate()
        apply()
        val handle = scope.resources.own { growthRefreshers.remove(key); value.remove(key); apply() }
        return Lease(handle) { next -> raw = next; recalculate(); apply() }
    }

    class Lease(private val handle: AutoCloseable, private val update: (Double) -> Unit) : AutoCloseable {
        private var closed = false
        fun setMultiplier(value: Double) { if (!closed) update(value) }
        override fun close() { if (!closed) { closed = true; handle.close() } }
    }
}
