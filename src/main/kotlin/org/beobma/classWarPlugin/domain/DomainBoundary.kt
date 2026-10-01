package org.beobma.classWarPlugin.domain

/** Coordinates are relative to the floor's center. Includes a one-block floor seal. */
class DomainBoundary(val radius: Double) {
    fun contains(x: Double, y: Double, z: Double) = y >= -1 && x * x + y * y + z * z < radius * radius
    fun crosses(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double): Boolean {
        if (ay < -1 && by < -1) return false
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val length = dx * dx + dy * dy + dz * dz
        if (length == 0.0) return contains(ax, ay, az)
        val lo = if (ay < -1) (-1 - ay) / dy else 0.0
        val hi = if (by < -1) (-1 - ay) / dy else 1.0
        val t = (-(ax * dx + ay * dy + az * dz) / length).coerceIn(lo, hi)
        return contains(ax + dx * t, ay + dy * t, az + dz * t)
    }
}
