package org.beobma.classWarPlugin.util

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Gate
import org.bukkit.block.data.type.TrapDoor
import java.util.ArrayDeque
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Conservative player-sized navigation used when choosing gameplay locations.
 *
 * A node represents either a two-block-high standing position or a water cell at
 * which a player can swim. Edges model normal walking, a one-block jump, leaving
 * the water onto a one-block bank, and a survivable three-block drop. Abilities,
 * block breaking/placing, flight, and parkour-only jumps are deliberately ignored.
 */
object PlayerNavigation {
    data class Node(val x: Int, val y: Int, val z: Int)

    data class Bounds(
        val minX: Double,
        val maxX: Double,
        val minZ: Double,
        val maxZ: Double,
    ) {
        fun contains(x: Double, z: Double): Boolean = x in minX..maxX && z in minZ..maxZ
        fun contains(node: Node): Boolean = contains(node.x + 0.5, node.z + 0.5)
    }

    private data class PathStep(val node: Node, val cost: Int, val estimate: Int) : Comparable<PathStep> {
        override fun compareTo(other: PathStep): Int =
            compareValuesBy(this, other, { it.cost + it.estimate }, { it.estimate })
    }

    fun nearestNode(world: World, location: Location, verticalSearch: Int = 3): Node? {
        if (location.world != world) return null
        val x = location.blockX
        val z = location.blockZ
        val baseY = location.blockY
        verticalOffsets(verticalSearch).forEach { offset ->
            val node = Node(x, baseY + offset, z)
            if (isNavigable(world, node)) return node
        }
        return null
    }

    /** Finds the exposed standing or surface-swimming node for a column. */
    fun surfaceNode(world: World, x: Int, z: Int): Node? {
        val highestY = world.getHighestBlockYAt(x, z)
        val candidates = intArrayOf(highestY + 1, highestY, highestY - 1, highestY - 2)
        candidates.forEach { y ->
            val node = Node(x, y, z)
            if (isNavigable(world, node)) return node
        }
        return null
    }

    /**
     * Finds safe land positions in a column, from the exposed surface down through
     * covered floors. Closed doors may be considered during pathfinding, but are
     * deliberately excluded here because a player must never be spawned inside one.
     */
    fun spawnableLandNodesInColumn(world: World, x: Int, z: Int, searchDepth: Int): List<Node> {
        val highestY = world.getHighestBlockYAt(x, z)
        val minimumY = maxOf(world.minHeight + 1, highestY + 1 - searchDepth.coerceAtLeast(0))
        return (highestY + 1 downTo minimumY).mapNotNull { y ->
            Node(x, y, z).takeIf { isSpawnableStanding(world, it) }
        }
    }

    fun isSpawnableLandNode(world: World, node: Node): Boolean = isSpawnableStanding(world, node)

    fun hasPathToArea(
        world: World,
        start: Node,
        centerX: Double,
        centerZ: Double,
        targetRadius: Double,
        targetNodes: Set<Node>? = null,
        bounds: Bounds,
        maxVisitedNodes: Int,
        deadlineNanos: Long = Long.MAX_VALUE,
    ): Boolean {
        if (!bounds.contains(start) || !isNavigable(world, start)) return false
        val radiusSquared = targetRadius * targetRadius
        if (isInsideTarget(start, centerX, centerZ, radiusSquared, targetNodes)) return true

        val open = PriorityQueue<PathStep>()
        val costs = hashMapOf(start to 0)
        open += PathStep(start, 0, areaHeuristic(start, centerX, centerZ, targetRadius))
        var visited = 0

        while (open.isNotEmpty() && visited < maxVisitedNodes) {
            if (System.nanoTime() >= deadlineNanos) return false
            val step = open.poll()
            if (costs[step.node] != step.cost) continue
            visited++
            if (isInsideTarget(step.node, centerX, centerZ, radiusSquared, targetNodes)) return true

            neighbours(world, step.node, bounds).forEach { next ->
                if (System.nanoTime() >= deadlineNanos) return false
                val verticalCost = abs(next.y - step.node.y) * 2
                val nextCost = step.cost + 10 + verticalCost
                if (nextCost >= (costs[next] ?: Int.MAX_VALUE)) return@forEach
                costs[next] = nextCost
                open += PathStep(next, nextCost, areaHeuristic(next, centerX, centerZ, targetRadius))
            }
        }
        return false
    }

    fun collectReachable(
        world: World,
        start: Node,
        bounds: Bounds,
        maxVisitedNodes: Int,
    ): List<Node> {
        if (!bounds.contains(start) || !isNavigable(world, start)) return emptyList()
        val queue = ArrayDeque<Node>()
        val visited = linkedSetOf(start)
        queue += start
        while (queue.isNotEmpty() && visited.size < maxVisitedNodes) {
            val current = queue.removeFirst()
            neighbours(world, current, bounds).forEach { next ->
                if (visited.size >= maxVisitedNodes) return@forEach
                if (visited.add(next)) queue += next
            }
        }
        return visited.toList()
    }

    fun displayLocation(world: World, node: Node): Location {
        val heightOffset = if (isSwimming(world, node)) 0.65 else 0.3
        return Location(world, node.x + 0.5, node.y + heightOffset, node.z + 0.5)
    }

    fun playerLocation(world: World, node: Node): Location {
        val heightOffset = if (isSwimming(world, node)) 0.1 else 0.0
        return Location(world, node.x + 0.5, node.y + heightOffset, node.z + 0.5)
    }

    fun isNavigable(world: World, node: Node): Boolean =
        isStanding(world, node) || isSwimming(world, node)

    private fun neighbours(world: World, current: Node, bounds: Bounds): List<Node> {
        val result = ArrayList<Node>(4)
        val verticalOffsets = if (isSwimming(world, current)) SWIMMING_VERTICAL_OFFSETS else WALKING_VERTICAL_OFFSETS
        for ((dx, dz) in DIRECTIONS) {
            val x = current.x + dx
            val z = current.z + dz
            if (!bounds.contains(x + 0.5, z + 0.5)) continue
            for (offsetY in verticalOffsets) {
                val candidate = Node(x, current.y + offsetY, z)
                if (!isNavigable(world, candidate) || !hasTransitionClearance(world, current, candidate)) {
                    continue
                }
                result += candidate
                break
            }
        }
        return result
    }

    private fun hasTransitionClearance(world: World, from: Node, to: Node): Boolean {
        val deltaY = to.y - from.y
        if (deltaY > 1 && !isSwimming(world, from)) return false

        if (deltaY > 0) {
            for (y in from.y + 2..to.y + 1) {
                if (!isBodyCellTraversable(world.getBlockAt(from.x, y, from.z))) return false
            }
        } else if (deltaY < 0) {
            for (y in to.y..from.y + 1) {
                if (!isBodyCellTraversable(world.getBlockAt(to.x, y, to.z))) return false
            }
        }
        return true
    }

    private fun isStanding(world: World, node: Node): Boolean {
        if (node.y - 1 < world.minHeight || node.y + 1 >= world.maxHeight) return false
        val feet = world.getBlockAt(node.x, node.y, node.z)
        val head = world.getBlockAt(node.x, node.y + 1, node.z)
        val ground = world.getBlockAt(node.x, node.y - 1, node.z)
        if (isWaterCell(feet) || !ground.type.isSolid || ground.type in UNSAFE_MATERIALS) return false
        return isBodyCellTraversable(feet) && isBodyCellTraversable(head)
    }

    private fun isSpawnableStanding(world: World, node: Node): Boolean {
        if (!isStanding(world, node)) return false
        val feet = world.getBlockAt(node.x, node.y, node.z)
        val head = world.getBlockAt(node.x, node.y + 1, node.z)
        return feet.isPassable && head.isPassable
    }

    private fun isSwimming(world: World, node: Node): Boolean {
        if (node.y < world.minHeight || node.y + 1 >= world.maxHeight) return false
        val feet = world.getBlockAt(node.x, node.y, node.z)
        val head = world.getBlockAt(node.x, node.y + 1, node.z)
        return isWaterCell(feet) && isBodyCellTraversable(feet) && isBodyCellTraversable(head)
    }

    private fun isBodyCellTraversable(block: Block): Boolean {
        if (block.type in UNSAFE_MATERIALS) return false
        if (block.isPassable) return true
        return when (block.blockData) {
            is Door -> block.type != Material.IRON_DOOR || hasNearbyPlayerActivator(block)
            is TrapDoor -> block.type != Material.IRON_TRAPDOOR || hasNearbyPlayerActivator(block)
            is Gate -> true
            else -> false
        }
    }

    /** Iron barriers are usable only when a player-operated control is mounted nearby. */
    private fun hasNearbyPlayerActivator(barrier: Block): Boolean {
        val blockData = barrier.blockData
        val baseY = if (blockData is Door && blockData.half == Bisected.Half.TOP) barrier.y - 1 else barrier.y
        for (x in barrier.x - ACTIVATOR_SEARCH_RADIUS..barrier.x + ACTIVATOR_SEARCH_RADIUS) {
            for (y in baseY - 1..baseY + 2) {
                for (z in barrier.z - ACTIVATOR_SEARCH_RADIUS..barrier.z + ACTIVATOR_SEARCH_RADIUS) {
                    if (isPlayerActivator(barrier.world.getBlockAt(x, y, z).type)) return true
                }
            }
        }
        return false
    }

    private fun isPlayerActivator(material: Material): Boolean {
        val name = material.name
        return material == Material.LEVER ||
            name.endsWith("_BUTTON") ||
            name.endsWith("_PRESSURE_PLATE")
    }

    private fun isWaterCell(block: Block): Boolean {
        if (block.type == Material.WATER || block.type == Material.BUBBLE_COLUMN) return true
        return block.type.name.contains("SEAGRASS") || block.type.name.startsWith("KELP")
    }

    private fun isInsideTarget(
        node: Node,
        centerX: Double,
        centerZ: Double,
        radiusSquared: Double,
        targetNodes: Set<Node>?,
    ): Boolean {
        val dx = node.x + 0.5 - centerX
        val dz = node.z + 0.5 - centerZ
        return dx * dx + dz * dz <= radiusSquared && (targetNodes == null || node in targetNodes)
    }

    private fun areaHeuristic(node: Node, centerX: Double, centerZ: Double, radius: Double): Int {
        val dx = node.x + 0.5 - centerX
        val dz = node.z + 0.5 - centerZ
        return (sqrt(dx * dx + dz * dz) - radius).coerceAtLeast(0.0).toInt() * 10
    }

    private fun verticalOffsets(radius: Int): IntArray {
        val offsets = ArrayList<Int>(radius * 2 + 1)
        offsets += 0
        for (distance in 1..radius) {
            offsets += distance
            offsets += -distance
        }
        return offsets.toIntArray()
    }

    private val DIRECTIONS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private const val ACTIVATOR_SEARCH_RADIUS = 2
    private val WALKING_VERTICAL_OFFSETS = intArrayOf(1, 0, -1, -2, -3)
    private val SWIMMING_VERTICAL_OFFSETS = intArrayOf(2, 1, 0, -1, -2, -3)
    private val UNSAFE_MATERIALS = setOf(
        Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW,
        Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE,
    )
}
