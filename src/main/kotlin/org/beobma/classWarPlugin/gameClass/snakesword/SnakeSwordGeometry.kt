package org.beobma.classWarPlugin.gameClass.snakesword

import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.*

internal object SnakeSwordGeometry {
    const val SEGMENTS = 16
    fun repeatDamage(base: Double, previousHits: Int) = (base / 2.0.pow(previousHits.coerceIn(0, 20))).coerceAtLeast(1.0)
    fun speedDamage(distance: Double) = if (distance < 0.08 || !distance.isFinite()) 0.0 else (distance * 4.0).coerceIn(1.0, 5.0)

    fun blade(origin: Vector, direction: Vector, length: Double, bend: Double): List<Vector> {
        val forward = direction.clone().normalize()
        val right = forward.clone().crossProduct(Vector(0, 1, 0)).let {
            if (it.lengthSquared() < 1e-6) Vector(1, 0, 0) else it.normalize()
        }
        return (0..SEGMENTS).map { index ->
            val t = index.toDouble() / SEGMENTS
            origin.clone().add(forward.clone().multiply(length * t))
                .add(right.clone().multiply(sin(t * PI) * bend))
        }
    }

    /** Sweep the entire moving blade, not only its tip or its end-of-tick pose. */
    fun contact(box: BoundingBox, before: List<Vector>, after: List<Vector>, radius: Double = 0.22): Double? {
        require(before.size == after.size)
        if (after.size < 2) return null
        val points = before + after
        val bounds = BoundingBox(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z },
            points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z }).expand(radius)
        if (!bounds.overlaps(box)) return null
        var speed: Double? = null
        for (i in 1 until after.size) {
            val displacement = max(before[i].distance(after[i]), before[i-1].distance(after[i-1]))
            val steps = ceil(displacement / 0.2).toInt().coerceIn(1, 160)
            for (step in 0..steps) {
                val t = step.toDouble() / steps
                val a = before[i-1].clone().multiply(1-t).add(after[i-1].clone().multiply(t))
                val b = before[i].clone().multiply(1-t).add(after[i].clone().multiply(t))
                if (HitboxUtil.intersectsSegment(box, a, b, radius)) {
                    speed = max(speed ?: 0.0, displacement)
                    break
                }
            }
        }
        return speed
    }
}
