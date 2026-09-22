package org.beobma.classWarPlugin.domain

import kotlin.math.ceil

/** Full thick voxel sphere, including the underside when cast in midair. */
internal object DomainShell {
    data class Cell(val x: Int, val y: Int, val z: Int)
    fun isInterior(radius: Int, x: Int, y: Int, z: Int): Boolean =
        x * x + (y + 0.5) * (y + 0.5) + z * z < radius * radius

    fun interiorCells(radius: Int): Sequence<Cell> = sequence {
        for (y in -radius..radius) for (x in -radius..radius) for (z in -radius..radius)
            if (isInterior(radius, x, y, z)) yield(Cell(x, y, z))
    }
    fun cells(radius: Int): List<Cell> = buildList {
        val outer = radius + 1.75
        val extent = ceil(outer).toInt()
        for (y in -extent..extent) for (x in -extent..extent) for (z in -extent..extent) {
            val horizontal = (x * x + z * z).toDouble()
            val distance = horizontal + (y + 0.5) * (y + 0.5)
            if (distance >= radius * radius && distance <= outer * outer) add(Cell(x, y, z))
        }
    }.sortedBy { it.y }

    fun frontHeight(radius: Int, progress: Double): Double =
        -(radius + 2.0) + 2 * (radius + 2.0) * progress.coerceIn(0.0, 1.0)

    fun fitsHeight(centerY: Int, radius: Int, minHeight: Int, maxHeight: Int) =
        centerY - radius - 2 >= minHeight && centerY + radius + 2 < maxHeight

    /** The court stays on its original horizontal plane inside the sphere. */
    fun floorCells(radius: Int): List<Cell> = buildList {
        for (x in -radius..radius) for (z in -radius..radius)
            if (x * x + z * z < radius * radius) add(Cell(x, -1, z))
    }
}
