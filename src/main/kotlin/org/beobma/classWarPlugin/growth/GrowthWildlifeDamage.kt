package org.beobma.classWarPlugin.growth

internal object GrowthWildlifeDamage {
    const val MAX_DAMAGE = 3.0
    fun baseDamage(level: Int): Double = (2.0 + level.coerceAtLeast(0) * 0.02).coerceAtMost(MAX_DAMAGE)
    fun limit(damage: Double): Double = damage.coerceIn(0.0, MAX_DAMAGE)
}
