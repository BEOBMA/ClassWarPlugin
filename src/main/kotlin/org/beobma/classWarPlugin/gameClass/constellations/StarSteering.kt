package org.beobma.classWarPlugin.gameClass.constellations

import org.bukkit.util.Vector
import kotlin.math.*

/** Bounded angular steering, including a stable turn when the target is directly behind. */
internal object StarSteering {
    const val GUIDANCE_TURN = 0.014

    /** Exterior guidance loses correction near impact, leaving a sprinting target an escape window. */
    fun exteriorTurn(base: Double, distance: Double): Double =
        if (base == GUIDANCE_TURN && distance <= 4.0) 0.0035 else base

    fun turn(heading: Vector, destination: Vector, limit: Double): Vector {
        val forward = heading.clone().normalize()
        if (destination.lengthSquared() < 1e-10) return forward
        val desired = destination.clone().normalize()
        val angle = acos(forward.dot(desired).coerceIn(-1.0, 1.0))
        if (angle <= limit) return desired
        var axis = forward.clone().crossProduct(desired)
        if (axis.lengthSquared() < 1e-10) axis = forward.clone().crossProduct(
            if (abs(forward.y) < 0.9) Vector(0.0,1.0,0.0) else Vector(1.0,0.0,0.0))
        return forward.rotateAroundAxis(axis.normalize(), limit).normalize()
    }
}
