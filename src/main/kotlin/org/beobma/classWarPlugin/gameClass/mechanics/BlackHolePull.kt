package org.beobma.classWarPlugin.gameClass.mechanics

import org.bukkit.util.Vector
import kotlin.math.min
import kotlin.math.sqrt

internal object BlackHolePull {
    /** A weak horizontal acceleration, never a replacement for walking/jumping velocity. */
    fun apply(velocity: Vector, displacementToCenter: Vector): Vector {
        val horizontal = displacementToCenter.clone().setY(0.0)
        val distance = sqrt(horizontal.lengthSquared())
        if (!distance.isFinite() || distance < 1.0E-6) return velocity.clone()
        return velocity.clone().add(horizontal.multiply(0.035 * min(distance, 1.0) / distance))
    }
}
