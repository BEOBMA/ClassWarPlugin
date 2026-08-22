package org.beobma.classWarPlugin.util

import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min

object HitboxUtil {
    fun closestPoint(box: BoundingBox, point: Vector): Vector = Vector(
        point.x.coerceIn(box.minX, box.maxX),
        point.y.coerceIn(box.minY, box.maxY),
        point.z.coerceIn(box.minZ, box.maxZ),
    )

    fun distanceSquared(box: BoundingBox, point: Vector): Double =
        closestPoint(box, point).distanceSquared(point)

    fun distanceSquared(first: BoundingBox, second: BoundingBox): Double {
        val dx = axisGap(first.minX, first.maxX, second.minX, second.maxX)
        val dy = axisGap(first.minY, first.maxY, second.minY, second.maxY)
        val dz = axisGap(first.minZ, first.maxZ, second.minZ, second.maxZ)
        return dx * dx + dy * dy + dz * dz
    }

    fun intersectsSphere(box: BoundingBox, center: Vector, radius: Double): Boolean =
        distanceSquared(box, center) <= radius * radius

    /** 레이가 박스와 만나는 시작점까지의 거리. 만나지 않으면 null. */
    fun rayIntersectionDistance(
        box: BoundingBox,
        origin: Vector,
        direction: Vector,
        maxDistance: Double,
        expansion: Double = 0.0,
    ): Double? {
        if (maxDistance < 0.0 || direction.lengthSquared() == 0.0) return null
        val expanded = if (expansion > 0.0) box.clone().expand(expansion) else box
        val normalized = direction.clone().normalize()
        var near = 0.0
        var far = maxDistance

        fun clip(originAxis: Double, directionAxis: Double, minimum: Double, maximum: Double): Boolean {
            if (kotlin.math.abs(directionAxis) < 1.0E-9) return originAxis in minimum..maximum
            var first = (minimum - originAxis) / directionAxis
            var second = (maximum - originAxis) / directionAxis
            if (first > second) first = second.also { second = first }
            near = max(near, first)
            far = min(far, second)
            return near <= far
        }

        if (!clip(origin.x, normalized.x, expanded.minX, expanded.maxX)) return null
        if (!clip(origin.y, normalized.y, expanded.minY, expanded.maxY)) return null
        if (!clip(origin.z, normalized.z, expanded.minZ, expanded.maxZ)) return null
        return near.takeIf { it <= maxDistance && far >= 0.0 }
    }

    fun intersectsSegment(box: BoundingBox, start: Vector, end: Vector, expansion: Double = 0.0): Boolean {
        val segment = end.clone().subtract(start)
        val length = segment.length()
        return rayIntersectionDistance(box, start, segment, length, expansion) != null
    }

    private fun axisGap(firstMin: Double, firstMax: Double, secondMin: Double, secondMax: Double): Double = when {
        firstMax < secondMin -> secondMin - firstMax
        secondMax < firstMin -> firstMin - secondMax
        else -> 0.0
    }
}
