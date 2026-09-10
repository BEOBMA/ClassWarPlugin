package org.beobma.classWarPlugin.gameClass.warCorrespondent

import kotlin.math.*
import org.bukkit.Location

/** Immutable cast direction shared by horizontal targeting and the projected particle grid. */
internal class RecordingGeometry(val yaw: Float, val pitch: Float) {
    data class Point(val x: Double, val y: Double, val z: Double)

    private val heading = Math.toRadians(yaw.toDouble())
    private val forwardX = -sin(heading)
    private val forwardZ = cos(heading)
    private val rightX = cos(heading)
    private val rightZ = sin(heading)

    fun contains(dx: Double, dz: Double): Boolean {
        val distance = hypot(dx, dz)
        return distance <= RANGE + 1e-8 &&
            (distance < 1e-8 || (dx * forwardX + dz * forwardZ) / distance >= 0.5 - 1e-8)
    }

    /** Clip the horizontal entity box to the 120-degree wedge, then test the radius. Y is ignored. */
    fun intersects(minX: Double, minZ: Double, maxX: Double, maxZ: Double): Boolean {
        var polygon = listOf(Point(minX, 0.0, minZ), Point(maxX, 0.0, minZ),
            Point(maxX, 0.0, maxZ), Point(minX, 0.0, maxZ))
        for (side in listOf(-1.0, 1.0)) {
            fun distance(p: Point) = sqrt(3.0) * (p.x * forwardX + p.z * forwardZ) +
                side * (p.x * rightX + p.z * rightZ)
            val clipped = mutableListOf<Point>()
            for (index in polygon.indices) {
                val a = polygon[index]
                val b = polygon[(index + 1) % polygon.size]
                val da = distance(a)
                val db = distance(b)
                if (da >= 0.0) clipped += a
                if ((da >= 0.0) != (db >= 0.0)) {
                    val t = da / (da - db)
                    clipped += Point(a.x + (b.x - a.x) * t, 0.0, a.z + (b.z - a.z) * t)
                }
            }
            polygon = clipped
            if (polygon.isEmpty()) return false
        }
        if (minX <= 0.0 && maxX >= 0.0 && minZ <= 0.0 && maxZ >= 0.0) return true
        return polygon.indices.any { index ->
            val a = polygon[index]
            val b = polygon[(index + 1) % polygon.size]
            val dx = b.x - a.x
            val dz = b.z - a.z
            val lengthSquared = dx * dx + dz * dz
            val t = if (lengthSquared < 1e-12) 0.0 else (-(a.x * dx + a.z * dz) / lengthSquared).coerceIn(0.0, 1.0)
            hypot(a.x + t * dx, a.z + t * dz) <= RANGE + 1e-8
        }
    }

    fun lockView(destination: Location): Location = destination.clone().also { it.yaw = yaw; it.pitch = pitch }

    private fun point(radius: Double, angle: Double, height: Double): Point {
        val radians = Math.toRadians(angle)
        val side = sin(radians) * radius
        val forward = cos(radians) * radius
        return Point(rightX * side + forwardX * forward, height, rightZ * side + forwardZ * forward)
    }

    // Concentric rows and radial columns fill the full sector, rather than only its edges.
    val floorGrid: List<Point> = buildList {
        for (radius in 2..16 step 2) {
            val samples = ceil(radius * Math.toRadians(120.0) / 0.8).toInt()
            for (i in 0..samples) add(point(radius.toDouble(), -60.0 + 120.0 * i / samples, 0.15))
        }
        for (angle in -60..60 step 10) for (step in 1..20) add(point(step * 0.8, angle.toDouble(), 0.15))
    }
    // A curved screen at exactly the same 16-block limit as the hit test.
    val screenGrid: List<Point> = buildList {
        for (angle in -60..60 step 5) for (row in 0..8) add(point(RANGE, angle.toDouble(), 0.15 + row * 0.5))
    }
    val screenBorder: List<Point> = buildList {
        for (angle in -60..60 step 2) {
            add(point(RANGE, angle.toDouble(), 0.15))
            add(point(RANGE, angle.toDouble(), 4.15))
        }
        for (row in 1..9) for (angle in listOf(-60.0, 60.0)) add(point(RANGE, angle, 0.15 + row * 0.4))
    }

    companion object {
        const val RANGE = 16.0
        const val CAMERA_RANGE = 8.0
        fun cameraCooldownTicks(filmedTicks: Int): Int = filmedTicks.coerceIn(20, 60)
    }
}
