package org.beobma.classWarPlugin.gameClass.swordplay

import org.beobma.classWarPlugin.entity.EntityData
import org.bukkit.Location
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Vector
import kotlin.math.*

internal enum class SwordFlightMode {
    OWNER_ORBIT,
    APPROACHING,
    TARGET_ORBIT,
    RETURNING,
}

internal data class FlyingSword(
    val display: ItemDisplay,
    var position: Location,
    var target: EntityData? = null,
    var attackCooldownTicks: Int = 0,
    var mode: SwordFlightMode = SwordFlightMode.OWNER_ORBIT,
    var movementSpeed: Double = 0.0,
    var targetOrbitAngle: Double = 0.0,
    var targetOrbitPathSpeed: Double = 0.0,
    var targetOrbitHitArmed: Boolean = false,
    var pierceForward: Vector = Vector(1.0, 0.0, 0.0),
    var pierceLoopAxis: Vector = Vector(0.0, 1.0, 0.0),
)

/** Pure path calculations; no world mutation, scheduler or damage dispatch. */
internal object SwordGeometry {
    private const val TARGET_PIERCE_LOOP_WIDTH_RATIO = 0.74
    private const val TARGET_PIERCE_MIN_DERIVATIVE_LENGTH = 0.001
    private const val TARGET_PIERCE_MIN_ANGLE_STEP = 0.004
    private const val TARGET_PIERCE_MAX_ANGLE_STEP = 0.16
    fun targetOrbitLocation(
        center: Location,
        sword: FlyingSword,
        angle: Double,
        radius: Double,
    ): Location {
        val wave = sin(angle)
        val forwardOffset = sword.pierceForward.clone().multiply(wave * radius)
        val loopOffset = sword.pierceLoopAxis.clone().multiply(
            wave * sin(2.0 * angle) * radius * TARGET_PIERCE_LOOP_WIDTH_RATIO,
        )
        return center.clone().add(forwardOffset).add(loopOffset)
    }

    fun targetOrbitTangent(sword: FlyingSword, angle: Double, radius: Double): Vector {
        val forwardDerivative = cos(angle) * radius
        val loopDerivative = (
            cos(angle) * sin(2.0 * angle) +
                2.0 * sin(angle) * cos(2.0 * angle)
            ) * radius * TARGET_PIERCE_LOOP_WIDTH_RATIO
        return sword.pierceForward.clone().multiply(forwardDerivative)
            .add(sword.pierceLoopAxis.clone().multiply(loopDerivative))
    }

    fun targetOrbitAngleStep(
        sword: FlyingSword,
        angle: Double,
        radius: Double,
        pathSpeed: Double,
    ): Double {
        val currentDerivativeLength = targetOrbitTangent(sword, angle, radius).length()
            .coerceAtLeast(TARGET_PIERCE_MIN_DERIVATIVE_LENGTH)
        val roughStep = (pathSpeed / currentDerivativeLength)
            .coerceIn(TARGET_PIERCE_MIN_ANGLE_STEP, TARGET_PIERCE_MAX_ANGLE_STEP)
        val midpointDerivativeLength = targetOrbitTangent(sword, angle + roughStep * 0.5, radius).length()
            .coerceAtLeast(TARGET_PIERCE_MIN_DERIVATIVE_LENGTH)
        return (pathSpeed / midpointDerivativeLength)
            .coerceIn(TARGET_PIERCE_MIN_ANGLE_STEP, TARGET_PIERCE_MAX_ANGLE_STEP)
    }

    fun normalizeOrbitAngle(angle: Double): Double {
        val fullTurn = 2.0 * PI
        val normalized = angle % fullTurn
        return if (normalized < 0.0) normalized + fullTurn else normalized
    }

    fun tiltedOrbitOffset(
        angle: Double,
        radius: Double,
        inclination: Double,
        yaw: Double,
    ): Vector {
        val localX = cos(angle) * radius
        val localY = sin(angle) * radius * sin(inclination)
        val localZ = sin(angle) * radius * cos(inclination)
        return Vector(
            localX * cos(yaw) - localZ * sin(yaw),
            localY,
            localX * sin(yaw) + localZ * cos(yaw),
        )
    }

}
