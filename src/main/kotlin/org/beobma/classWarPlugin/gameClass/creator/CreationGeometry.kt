package org.beobma.classWarPlugin.gameClass.creator

import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector

internal object CreationGeometry {
    /** Thirty points regardless of radius; degenerate aim uses a horizontal seal. */
    fun seal(radius: Double, normal: Vector): List<Vector> {
        val n = if (normal.lengthSquared() < 1e-10) Vector(0.0, 1.0, 0.0) else normal.clone().normalize()
        val u = n.clone().crossProduct(if (kotlin.math.abs(n.y) < 0.9) Vector(0.0, 1.0, 0.0) else Vector(1.0, 0.0, 0.0)).normalize()
        val v = n.clone().crossProduct(u).normalize()
        return buildList {
            repeat(24) { i ->
                val a = i * Math.PI / 12
                val offset = u.clone().multiply(kotlin.math.cos(a)*radius).add(v.clone().multiply(kotlin.math.sin(a)*radius))
                add(offset)
                if (i % 4 == 0) add(offset.clone().multiply(0.65))
            }
        }
    }
    /** Sweep a projectile along the whole tick segment, clipped to the first blocking surface. */
    fun contact(box: BoundingBox, start: Vector, direction: Vector, distance: Double, padding: Double): Vector? {
        val expanded = box.clone().expand(padding)
        if (expanded.contains(start)) return start.clone()
        if (direction.lengthSquared() < 1e-10 || distance <= 0) return null
        return expanded.rayTrace(start, direction.clone().normalize(), distance)?.hitPosition
    }
    fun inRadius(box: BoundingBox, center: Vector, radius: Double): Boolean {
        val nearest = Vector(center.x.coerceIn(box.minX, box.maxX), center.y.coerceIn(box.minY, box.maxY), center.z.coerceIn(box.minZ, box.maxZ))
        return nearest.distanceSquared(center) <= radius * radius
    }
}
