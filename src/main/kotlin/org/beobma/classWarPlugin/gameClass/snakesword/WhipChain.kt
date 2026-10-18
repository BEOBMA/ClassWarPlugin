package org.beobma.classWarPlugin.gameClass.snakesword

import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.*

/** Small Verlet/PBD rod solver: fixed grip, inertial links, length constraints and limited joint folding. */
internal class WhipChain(origin: Vector, direction: Vector, length: Double) {
    companion object {
        const val SUBSTEPS = 4
        private const val ITERATIONS = 10
        private const val JOINT_SPAN = 1.6
        private val DAMPING = 0.82.pow(1.0 / SUBSTEPS)
    }

    data class Motion(val frames: List<List<Vector>>, val rebased: Boolean = false) {
        val points get() = frames.last()
        /** Substep sweeps follow the simulated curve; a teleport must never sweep damage across the map. */
        fun contact(box: BoundingBox): Double? {
            if (rebased) return null
            var speed: Double? = null
            frames.zipWithNext().forEach { (before, after) ->
                SnakeSwordGeometry.contact(box, before, after)?.let {
                    speed = max(speed ?: 0.0, it * SUBSTEPS)
                }
            }
            return speed
        }
    }

    private var anchor = origin.clone()
    private var aim = safeDirection(direction)
    private var currentLength = safeLength(length)
    private var points = straight(anchor, aim, currentLength)
    private var previous = points.map { it.clone() }

    fun snapshot(): List<Vector> = points.map { it.clone() }

    fun advance(nextAnchor: Vector, direction: Vector, length: Double): Motion {
        val nextAim = safeDirection(direction, aim)
        val nextLength = safeLength(length)
        if (nextAnchor.distanceSquared(anchor) > max(6.0, nextLength * 0.75).pow(2) || nextLength <= 1e-6) {
            anchor = nextAnchor.clone(); aim = nextAim; currentLength = nextLength
            points = straight(anchor, aim, currentLength); previous = snapshot()
            return Motion(listOf(snapshot()), rebased = true)
        }
        val frames = mutableListOf(snapshot())
        val dt = 1.0 / SUBSTEPS
        repeat(SUBSTEPS) { substep ->
            val alpha = (substep + 1.0) / SUBSTEPS
            val grip = anchor.clone().multiply(1-alpha).add(nextAnchor.clone().multiply(alpha))
            aim = turnTowards(aim, nextAim, 0.30)
            val rest = (currentLength + (nextLength-currentLength)*alpha) / SnakeSwordGeometry.SEGMENTS
            val old = snapshot()
            for (i in 2..SnakeSwordGeometry.SEGMENTS) {
                val fraction = i.toDouble() / SnakeSwordGeometry.SEGMENTS
                val velocity = points[i].clone().subtract(previous[i]).multiply(DAMPING)
                // Bound numerical energy after violent reversals, without snapping links to the aiming line.
                if (velocity.lengthSquared() > 1.0) velocity.normalize()
                val target = grip.clone().add(aim.clone().multiply(rest * i))
                val force = target.subtract(points[i]).multiply((0.75 - 0.50*fraction)*dt*dt)
                force.y -= 0.035 * dt * dt
                points[i].add(velocity).add(force)
            }
            fun pinGrip() {
                points[0] = grip.clone()
                points[1] = grip.clone().add(aim.clone().multiply(rest))
            }
            pinGrip()
            repeat(ITERATIONS) {
                for (i in 1..SnakeSwordGeometry.SEGMENTS) constrain(i-1, i, rest)
                for (i in SnakeSwordGeometry.SEGMENTS downTo 2) {
                    // A minimum two-link span prevents adjacent blades folding through one another.
                    if (points[i].distanceSquared(points[i-2]) < (rest*JOINT_SPAN).pow(2))
                        constrain(i-2, i, rest*JOINT_SPAN)
                }
                for (i in SnakeSwordGeometry.SEGMENTS downTo 1) constrain(i-1, i, rest)
                pinGrip()
            }
            // Final anchored projection keeps all visible links exactly connected, even while extending.
            for (i in 2..SnakeSwordGeometry.SEGMENTS) {
                val tangent = safeDirection(points[i].clone().subtract(points[i-1]), aim)
                points[i] = points[i-1].clone().add(tangent.multiply(rest))
            }
            previous = old
            frames += snapshot()
        }
        anchor = nextAnchor.clone(); currentLength = nextLength
        return Motion(frames)
    }

    private fun constrain(a: Int, b: Int, distance: Double) {
        val delta = points[b].clone().subtract(points[a])
        val actual = delta.length()
        if (actual < 1e-9) {
            if (b > 1) points[b] = points[a].clone().add(aim.clone().multiply(distance))
            return
        }
        val weightA = if (a <= 1) 0.0 else 1.0
        val weightB = if (b <= 1) 0.0 else 1.0
        if (weightA + weightB == 0.0) return
        val correction = delta.multiply((actual-distance) / actual / (weightA+weightB))
        if (weightA > 0.0) points[a].add(correction)
        if (weightB > 0.0) points[b].subtract(correction)
    }

    private fun straight(origin: Vector, direction: Vector, length: Double) =
        (0..SnakeSwordGeometry.SEGMENTS).map { origin.clone().add(direction.clone().multiply(length*it/SnakeSwordGeometry.SEGMENTS)) }.toMutableList()

    private fun safeLength(length: Double) = if (length.isFinite()) length.coerceAtLeast(0.0) else 0.0

    private fun safeDirection(direction: Vector, fallback: Vector = Vector(0, 0, 1)): Vector =
        if (direction.x.isFinite() && direction.y.isFinite() && direction.z.isFinite() && direction.lengthSquared() > 1e-12)
            direction.clone().normalize() else fallback.clone()

    private fun turnTowards(from: Vector, to: Vector, maximum: Double): Vector {
        val angle = acos(from.dot(to).coerceIn(-1.0, 1.0))
        if (angle <= maximum) return to.clone()
        var axis = from.clone().crossProduct(to)
        if (axis.lengthSquared() < 1e-10) {
            axis = from.clone().crossProduct(if (abs(from.y) < 0.9) Vector(0, 1, 0) else Vector(1, 0, 0))
        }
        return from.clone().rotateAroundAxis(axis.normalize(), maximum).normalize()
    }
}
