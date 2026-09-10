package org.beobma.classWarPlugin.damage

import kotlin.math.roundToInt

internal object ShieldDamage {
    data class Result(val healthDamage: Double, val remainingShield: Int)
    fun calculate(damage: Double, shield: Int, multiplier: Double = 1.0): Result {
        val value = damage.roundToInt().coerceAtLeast(0)
        val factor = multiplier.takeIf { it.isFinite() && it >= 1.0 } ?: 1.0
        return Result((value - shield / factor).coerceAtLeast(0.0),
            (shield - value * factor).roundToInt().coerceAtLeast(0))
    }
}
