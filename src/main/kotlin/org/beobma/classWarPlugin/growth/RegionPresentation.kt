package org.beobma.classWarPlugin.growth

import kotlin.math.floor
import kotlin.random.Random

/** Proper place names are drawn without replacement, even when every region has the same biome. */
class RegionNames(random: Random) {
    private val names = listOf("아르덴", "벨로라", "칼리스", "델피나", "에리온", "페르나", "갈레온", "하르벤",
        "이셀라", "제피르", "카르멘", "루미나", "미레아", "노르딘", "오르비스", "파르헬",
        "퀼리아", "라베른", "실피아", "테라온", "우르델", "베르딘", "윈테라", "크레시아",
        "이그니스", "자르딘", "엘루나", "로엔", "네리안", "아스텔", "세르카", "브리엔").shuffled(random).iterator()
    fun next(terrain: String): String {
        val kind = when (terrain) {
            "forest" -> "숲"; "beach" -> "해변"; "mountain" -> "고원"; "snow" -> "설원"
            "desert" -> "사막"; "ruins" -> "유적"; else -> "평원"
        }
        return "${names.next()} $kind"
    }
}

/** 0: untouched, 1: dark halo, 2: white core. Terrain remains visible away from borders. */
object RegionMapBorders {
    fun mask(labels: IntArray, width: Int, height: Int): IntArray {
        require(labels.size == width * height)
        val result = IntArray(labels.size)
        val edges = labels.indices.filter { i ->
            labels[i] >= 0 && ((i % width + 1 < width && labels[i + 1] >= 0 && labels[i] != labels[i + 1]) ||
                (i / width + 1 < height && labels[i + width] >= 0 && labels[i] != labels[i + width]))
        }
        for (i in edges) for (dy in -1..1) for (dx in -1..1) {
            val x = i % width + dx; val y = i / width + dy
            if (x in 0 until width && y in 0 until height && labels[y * width + x] >= 0) result[y * width + x] = 1
        }
        edges.forEach { result[it] = 2 }
        return result
    }
}

data class RegionBoundaryPoint(val x: Double, val z: Double, val y: Int?, val first: Int, val second: Int) {
    fun state(layout: RegionLayout) = maxOf(layout.regions[first].state, layout.regions[second].state)
}

/** Bucketed once per layout; visual ticks never scan the entire map or load terrain chunks. */
class RegionBoundaries(layout: RegionLayout) {
    val points: List<RegionBoundaryPoint> = buildList {
        val s = layout.surface
        for (cell in layout.labels.indices) {
            val x = cell % s.size; val z = cell / s.size
            fun edge(other: Int, dx: Double, dz: Double) {
                if (layout.labels[cell] == layout.labels[other]) return
                val y = listOf(s.heights[cell], s.heights[other]).filter { it != SurfaceSnapshot.BLOCKED }.maxOrNull()
                add(RegionBoundaryPoint(s.originX + x + dx, s.originZ + z + dz, y, layout.labels[cell], layout.labels[other]))
            }
            if (x + 1 < s.size) edge(cell + 1, 1.0, 0.5)
            if (z + 1 < s.size) edge(cell + s.size, 0.5, 1.0)
        }
    }
    private val buckets = points.groupBy { bucket(it.x) to bucket(it.z) }
    private fun bucket(value: Double) = floor(value / 16).toInt()
    fun near(x: Double, z: Double, radius: Double = 24.0, limit: Int = 128): List<RegionBoundaryPoint> = buildList {
        for (bx in bucket(x - radius)..bucket(x + radius)) for (bz in bucket(z - radius)..bucket(z + radius))
            addAll(buckets[bx to bz].orEmpty())
    }.asSequence().filter { (it.x - x) * (it.x - x) + (it.z - z) * (it.z - z) <= radius * radius }
        .sortedBy { (it.x - x) * (it.x - x) + (it.z - z) * (it.z - z) }.take(limit).toList()
}
