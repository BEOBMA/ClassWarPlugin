package org.beobma.classWarPlugin.growth

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

data class RegionRect(val x: Int, val z: Int, val width: Int, val depth: Int) {
    val area get() = width * depth
    fun contains(px: Int, pz: Int) = px >= x && px < x + width && pz >= z && pz < z + depth
    fun touches(other: RegionRect): Boolean =
        ((x + width == other.x || other.x + other.width == x) && z < other.z + other.depth && other.z < z + depth) ||
            ((z + depth == other.z || other.z + other.depth == z) && x < other.x + other.width && other.x < x + width)
}

enum class RegionState(val label: String) { SAFE("안전"), WARNING("경고"), FORBIDDEN("금지") }

data class GrowthRegion(val id: Int, val rectangles: List<RegionRect>, val name: String, val terrain: String,
    val walkable: IntArray, val neighbors: Set<Int>, val finalSquare: RegionRect?) {
    var state: RegionState = RegionState.SAFE
}

/** Only plain arrays cross the worker boundary. NO Bukkit world access here. */
data class SurfaceSnapshot(val originX: Int, val originZ: Int, val size: Int,
    val heights: IntArray, val terrain: Array<String>) {
    companion object { const val BLOCKED = Int.MIN_VALUE }
    fun neighbors(index: Int): IntArray {
        val x = index % size; val z = index / size
        return intArrayOf(if (x > 0) index - 1 else -1, if (x < size - 1) index + 1 else -1,
            if (z > 0) index - size else -1, if (z < size - 1) index + size else -1)
    }
    fun canWalk(a: Int, b: Int) = a >= 0 && b >= 0 && heights[a] != BLOCKED && heights[b] != BLOCKED &&
        abs(heights[a].toLong() - heights[b].toLong()) <= 1L
}

class RegionLayout(val surface: SurfaceSnapshot, val regions: List<GrowthRegion>, val labels: IntArray, val seed: Int) {
    fun at(x: Double, z: Double): GrowthRegion? {
        val px = floor(x).toInt() - surface.originX; val pz = floor(z).toInt() - surface.originZ
        if (px !in 0 until surface.size || pz !in 0 until surface.size) return null
        return regions[labels[pz * surface.size + px]]
    }
    fun connected(ids: Set<Int>): Boolean {
        if (ids.isEmpty()) return false
        val seen = mutableSetOf(ids.first()); val queue = ArrayDeque<Int>(); queue.add(ids.first())
        while (queue.isNotEmpty()) for (next in regions[queue.removeFirst()].neighbors) {
            if (next in ids && seen.add(next)) queue.add(next)
        }
        return seen.size == ids.size
    }
}

object RegionGenerator {
    fun generate(surface: SurfaceSnapshot, settings: GrowthSettings, minimumFinalSize: Int, seed: Int, cancelled: () -> Boolean = { false }): RegionLayout {
        val random = Random(seed)
        val deadline = System.nanoTime() + 30_000_000_000L
        for (count in settings.maximumRegions downTo settings.minimumRegions) {
            repeat(settings.generationAttempts) {
                if (cancelled() || Thread.currentThread().isInterrupted) error("지역 생성 취소")
                if (System.nanoTime() > deadline) error("지역 분석이 30초를 초과했습니다. 지역 수나 맵 크기를 줄여 주세요.")
                val rectangles = mutableListOf(RegionRect(0, 0, surface.size, surface.size))
                while (rectangles.size < count * 3) {
                    val rect = rectangles.filter { it.width >= 16 || it.depth >= 16 }
                        .maxByOrNull { it.area * random.nextDouble(0.7, 1.3) } ?: break
                    rectangles.remove(rect)
                    val vertical = if (rect.width < 16) false else if (rect.depth < 16) true
                        else rect.width * random.nextDouble(0.65, 1.35) > rect.depth
                    val length = if (vertical) rect.width else rect.depth
                    val cut = List(4) { (length * random.nextDouble(0.32, 0.68)).toInt().coerceIn(6, length - 6) }
                        .maxBy { candidate ->
                            val span = if (vertical) rect.depth else rect.width
                            var contrast = 0.0
                            for (offset in 0 until span step 4) {
                                val a = if (vertical) (rect.z + offset) * surface.size + rect.x + candidate - 1
                                    else (rect.z + candidate - 1) * surface.size + rect.x + offset
                                val b = a + if (vertical) 1 else surface.size
                                if (surface.terrain[a] != surface.terrain[b]) contrast += 1.0
                                if (!surface.canWalk(a, b)) contrast += 0.5
                            }
                            contrast / (span / 4 + 1) + random.nextDouble(0.0, 0.25)
                        }
                    if (vertical) {
                        rectangles += RegionRect(rect.x, rect.z, cut, rect.depth)
                        rectangles += RegionRect(rect.x + cut, rect.z, rect.width - cut, rect.depth)
                    } else {
                        rectangles += RegionRect(rect.x, rect.z, rect.width, cut)
                        rectangles += RegionRect(rect.x, rect.z + cut, rect.width, rect.depth - cut)
                    }
                }
                val groups = rectangles.map { mutableListOf(it) }.toMutableList()
                while (groups.size > count) {
                    val a = groups.indices.minBy { groups[it].sumOf(RegionRect::area) * random.nextDouble(0.8, 1.2) }
                    val b = groups.indices.filter { it != a && groups[a].any { r -> groups[it].any(r::touches) } }
                        .minByOrNull { groups[it].sumOf(RegionRect::area) * random.nextDouble(0.75, 1.25) } ?: break
                    groups[a].addAll(groups[b]); groups.removeAt(b)
                }
                build(surface, groups, minimumFinalSize, seed, random)?.let { return it }
            }
        }
        error("연결된 보행 지역 ${settings.minimumRegions}개와 ${minimumFinalSize}블록 최종 안전지대를 확보하지 못했습니다. 평지가 더 많은 맵이나 작은 최종 자기장을 사용하세요. (seed=$seed)")
    }

    private fun build(s: SurfaceSnapshot, groups: List<List<RegionRect>>, finalSize: Int, seed: Int, random: Random): RegionLayout? {
        val labels = IntArray(s.size * s.size) { -1 }
        groups.forEachIndexed { id, group -> group.forEach { r ->
            for (z in r.z until r.z + r.depth) for (x in r.x until r.x + r.width) labels[z * s.size + x] = id
        } }
        if (labels.any { it < 0 }) return null
        // Each region exposes ONLY its largest continuous walkable surface component.
        val visited = BooleanArray(labels.size); val components = Array(groups.size) { intArrayOf() }
        for (start in labels.indices) {
            if (visited[start] || s.heights[start] == SurfaceSnapshot.BLOCKED) continue
            val id = labels[start]; val queue = ArrayDeque<Int>(); val cells = ArrayList<Int>()
            visited[start] = true; queue.add(start)
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst(); cells.add(cell)
                for (n in s.neighbors(cell)) if (n >= 0 && !visited[n] && labels[n] == id && s.canWalk(cell, n)) {
                    visited[n] = true; queue.add(n)
                }
            }
            if (cells.size > components[id].size) components[id] = cells.toIntArray()
        }
        if (components.any { it.size < 32 }) return null
        val retained = BooleanArray(labels.size)
        components.forEach { cells -> cells.forEach { retained[it] = true } }
        val links = Array(groups.size) { mutableSetOf<Int>() }
        for (cell in labels.indices) if (retained[cell]) {
            for (n in s.neighbors(cell)) if (n >= 0 && retained[n] && labels[cell] != labels[n] && s.canWalk(cell, n)) {
                links[labels[cell]].add(labels[n])
            }
        }
        // Largest homogeneous square per region: prevents the final border crossing a forbidden region.
        val squares = arrayOfNulls<RegionRect>(groups.size)
        val dp = IntArray(labels.size)
        for (cell in labels.indices) {
            val x = cell % s.size; val z = cell / s.size; val id = labels[cell]
            dp[cell] = if (x > 0 && z > 0 && labels[cell - 1] == id && labels[cell - s.size] == id &&
                labels[cell - s.size - 1] == id) 1 + minOf(dp[cell - 1], dp[cell - s.size], dp[cell - s.size - 1]) else 1
            val side = dp[cell]
            if (side >= finalSize && side > (squares[id]?.width ?: 0)) {
                val center = (z - side + 1 + side / 2) * s.size + x - side + 1 + side / 2
                if (retained[center]) squares[id] = RegionRect(x - side + 1, z - side + 1, side, side)
            }
        }
        if (squares.all { it == null }) return null
        val names = RegionNames(random)
        val regions = groups.mapIndexed { id, group ->
            val tag = components[id].asSequence().groupingBy { s.terrain[it] }.eachCount().maxBy { it.value }.key
            GrowthRegion(id, group.toList(), names.next(tag), tag,
                components[id], links[id].toSet(), squares[id])
        }
        val result = RegionLayout(s, regions, labels, seed)
        return result.takeIf { it.connected(regions.indices.toSet()) }
    }
}

/** Maintains a connected SAFE set and a direct escape from EVERY warning region. */
class RegionSchedule(val layout: RegionLayout, private val warningCount: Int, seed: Int) {
    private val random = Random(seed)
    var phaseIndex = 0; private set
    val day get() = phaseIndex / 2 + 1
    val night get() = phaseIndex % 2 == 1
    val remaining get() = layout.regions.filter { it.state != RegionState.FORBIDDEN }
    val finalRegion get() = remaining.singleOrNull()
    init { warn() }
    fun advance() {
        phaseIndex++
        layout.regions.filter { it.state == RegionState.WARNING }.forEach { it.state = RegionState.FORBIDDEN }
        if (remaining.size > 1) warn()
    }
    private fun warn() {
        val safe = remaining.mapTo(mutableSetOf()) { it.id }
        val warnings = mutableSetOf<Int>()
        repeat(warningCount) {
            if (safe.size <= 1) return@repeat
            val candidate = safe.shuffled(random).firstOrNull { id ->
                val next = safe - id
                next.any { layout.regions[it].finalSquare != null } && layout.connected(next) &&
                    (warnings + id).all { w -> layout.regions[w].neighbors.any { it in next } }
            } ?: return@repeat
            safe.remove(candidate); warnings.add(candidate)
        }
        check(warnings.isNotEmpty() || safe.size == 1) { "금지구역 연결성 불변식 위반" }
        warnings.forEach { layout.regions[it].state = RegionState.WARNING }
    }
}
