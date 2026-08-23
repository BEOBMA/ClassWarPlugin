package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.ArrayDeque
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

class Exodia : GameClass(), GameStatusHandler {
    override val name = "<gray>엑조디아"
    override val rank = Rank.B
    override val classItemMaterial = Material.TRIAL_KEY
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private data class Part(val label: String, val material: Material, val display: ItemDisplay, val baseY: Double)
    private data class WalkNode(val x: Int, val y: Int, val z: Int)
    private data class PathStep(val node: WalkNode, val cost: Int, val estimate: Int) : Comparable<PathStep> {
        override fun compareTo(other: PathStep): Int =
            compareValuesBy(this, other, { it.cost + it.estimate }, { it.estimate })
    }

    private val parts = mutableListOf<Part>()
    private var collected = 0

    override fun onBattleStart() {
        parts.forEach { it.display.remove() }
        parts.clear()
        collected = 0
        playerData.getOrCreateStatus(playerData) { ExodiaPartStatus() }.updatePower(0)
        val definitions = listOf(
            "왼쪽 팔" to Material.GOLDEN_AXE,
            "오른쪽 팔" to Material.GOLDEN_SWORD,
            "왼쪽 다리" to Material.GOLDEN_BOOTS,
            "오른쪽 다리" to Material.CHAINMAIL_BOOTS,
            "몸통" to Material.GOLDEN_CHESTPLATE,
        )
        val partLocations = findPartLocations(definitions.size)
        definitions.forEachIndexed { index, (label, material) ->
            val location = partLocations[index]
            val display = location.world.spawn(location, ItemDisplay::class.java).apply {
                setItemStack(ItemStack(material))
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                billboard = Display.Billboard.FIXED
                brightness = Display.Brightness(15, 15)
                transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(1.15f, 1.15f, 1.15f), Quaternionf())
                isPersistent = false
                setRotation(index * 72f, 0f)
            }
            TemporaryDisplayManager.mark(display, player.uniqueId)
            parts += Part(label, material, display, location.y)
        }
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead || parts.isEmpty()) {
                    cancel()
                    return
                }
                parts.toList().forEachIndexed { index, part ->
                    if (!part.display.isValid) {
                        parts.remove(part)
                        return@forEachIndexed
                    }
                    val location = part.display.location
                    location.y = part.baseY + kotlin.math.sin(tick * 0.11 + index) * 0.22
                    part.display.teleport(location)
                    part.display.setRotation((tick * 3f + index * 72f) % 360f, 0f)
                    if (tick % 5 == 0) particles.spawn(location, Particle.ENCHANT, count = 5, spread = 0.32, speed = 0.02)
                    if (HitboxUtil.intersectsSphere(player.boundingBox, location.toVector(), 1.35)) collect(part)
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }
    override fun onGameTimePasses() = Unit

    private fun findPartLocations(count: Int): List<Location> {
        val world = player.world
        val half = (game.settings.borderInitialSize * 0.45).coerceAtLeast(6.0)
        val start = standingNodeAt(player.location) ?: WalkNode(player.location.blockX, player.location.blockY, player.location.blockZ)
        val locations = mutableListOf<Location>()

        repeat(PART_LOCATION_ATTEMPTS) {
            if (locations.size >= count) return locations
            val x = game.roundCenterX + Random.nextDouble(-half, half)
            val z = game.roundCenterZ + Random.nextDouble(-half, half)
            val standing = surfaceStandingNode(world, floor(x).toInt(), floor(z).toInt()) ?: return@repeat
            val location = partDisplayLocation(world, standing)
            if (locations.none { it.distanceSquared(location) < MINIMUM_PART_DISTANCE_SQUARED } &&
                hasWalkingPath(world, start, standing, half)
            ) locations += location
        }

        // Random targets can fail on maps split by cliffs or buildings. Fill the remainder only
        // from nodes reached by an actual player-sized walk starting at the Exodia player.
        val reachable = collectNearbyWalkableNodes(world, start, half)
        reachable.shuffled().forEach { node ->
            if (locations.size >= count) return@forEach
            val location = partDisplayLocation(world, node)
            if (locations.none { it.distanceSquared(location) < MINIMUM_PART_DISTANCE_SQUARED }) locations += location
        }
        reachable.forEach { node ->
            if (locations.size >= count) return@forEach
            val location = partDisplayLocation(world, node)
            if (locations.none { it.blockX == location.blockX && it.blockY == location.blockY && it.blockZ == location.blockZ }) {
                locations += location
            }
        }

        val safeFallback = partDisplayLocation(world, start)
        while (locations.size < count) locations += safeFallback.clone()
        return locations
    }

    private fun hasWalkingPath(world: World, start: WalkNode, target: WalkNode, half: Double): Boolean {
        if (!isSafeStandingNode(world, start) || !isSafeStandingNode(world, target)) return false
        if (start == target) return true
        val open = PriorityQueue<PathStep>()
        val costs = hashMapOf(start to 0)
        open += PathStep(start, 0, walkingHeuristic(start, target))
        var visited = 0

        while (open.isNotEmpty() && visited++ < PATH_MAX_VISITED_NODES) {
            val step = open.poll()
            if (costs[step.node] != step.cost) continue
            if (step.node == target) return true
            walkingNeighbours(world, step.node, half).forEach { next ->
                val nextCost = step.cost + 1 + abs(next.y - step.node.y)
                if (nextCost >= (costs[next] ?: Int.MAX_VALUE)) return@forEach
                costs[next] = nextCost
                open += PathStep(next, nextCost, walkingHeuristic(next, target))
            }
        }
        return false
    }

    private fun collectNearbyWalkableNodes(world: World, start: WalkNode, half: Double): List<WalkNode> {
        if (!isSafeStandingNode(world, start)) return emptyList()
        val queue = ArrayDeque<WalkNode>()
        val visited = linkedSetOf(start)
        queue += start
        while (queue.isNotEmpty() && visited.size < FALLBACK_MAX_VISITED_NODES) {
            val current = queue.removeFirst()
            walkingNeighbours(world, current, half).forEach { next ->
                if (visited.add(next)) queue += next
            }
        }
        return visited.toList()
    }

    private fun walkingNeighbours(world: World, current: WalkNode, half: Double): List<WalkNode> {
        val result = ArrayList<WalkNode>(4)
        WALK_DIRECTIONS.forEach { (dx, dz) ->
            val x = current.x + dx
            val z = current.z + dz
            if (!isInsidePartArea(world, x, z, half)) return@forEach
            for (offsetY in WALKABLE_VERTICAL_OFFSETS) {
                val candidate = WalkNode(x, current.y + offsetY, z)
                if (!isSafeStandingNode(world, candidate)) continue
                if (offsetY > 0 && !isWalkThrough(world, current.x, current.y + 2, current.z)) continue
                if (offsetY < 0 && (candidate.y + 2..current.y + 1).any { y -> !isWalkThrough(world, x, y, z) }) continue
                result += candidate
                break
            }
        }
        return result
    }

    private fun standingNodeAt(location: Location): WalkNode? {
        val world = location.world
        val direct = WalkNode(location.blockX, location.blockY, location.blockZ)
        if (isSafeStandingNode(world, direct)) return direct
        return (-2..2).firstNotNullOfOrNull { offset ->
            WalkNode(location.blockX, location.blockY + offset, location.blockZ).takeIf { isSafeStandingNode(world, it) }
        }
    }

    private fun surfaceStandingNode(world: World, x: Int, z: Int): WalkNode? {
        val groundY = world.getHighestBlockYAt(x, z)
        val node = WalkNode(x, groundY + 1, z)
        return node.takeIf { isSafeStandingNode(world, it) }
    }

    private fun isSafeStandingNode(world: World, node: WalkNode): Boolean {
        if (node.y - 1 < world.minHeight || node.y + 1 >= world.maxHeight) return false
        val ground = world.getBlockAt(node.x, node.y - 1, node.z)
        return ground.type.isSolid && ground.type !in UNSAFE_WALK_MATERIALS &&
            isWalkThrough(world, node.x, node.y, node.z) &&
            isWalkThrough(world, node.x, node.y + 1, node.z)
    }

    private fun isWalkThrough(world: World, x: Int, y: Int, z: Int): Boolean {
        val block = world.getBlockAt(x, y, z)
        return block.isPassable && !block.isLiquid && block.type !in UNSAFE_WALK_MATERIALS
    }

    private fun isInsidePartArea(world: World, x: Int, z: Int, half: Double): Boolean {
        val location = Location(world, x + 0.5, world.minHeight.toDouble(), z + 0.5)
        return abs(location.x - game.roundCenterX) <= half &&
            abs(location.z - game.roundCenterZ) <= half && world.worldBorder.isInside(location)
    }

    private fun walkingHeuristic(from: WalkNode, to: WalkNode): Int =
        abs(from.x - to.x) + abs(from.z - to.z) + abs(from.y - to.y)

    private fun partDisplayLocation(world: World, node: WalkNode): Location =
        Location(world, node.x + 0.5, node.y + 0.3, node.z + 0.5)

    private fun collect(part: Part) {
        if (!parts.remove(part)) return
        part.display.remove()
        collected++
        playerData.getOrCreateStatus(playerData) { ExodiaPartStatus() }.updatePower(collected)
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 42, spread = 0.65, speed = 0.13)
        sounds.play(player, Sound.ENTITY_ITEM_PICKUP, volume = 0.9f, pitch = 0.8f + collected * 0.12f)
        player.sendMessage(net.kyori.adventure.text.Component.text("[엑조디아] ${part.label}을(를) 획득했습니다. ($collected/5)"))
        if (collected < 5) return
        particles.spawn(player, Particle.FLASH, count = 3)
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 180, spread = 1.5, speed = 0.25)
        sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, volume = 1.0f, pitch = 0.65f)
        game.playerDatas.filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead }
            .forEach { it.player.health = 0.0 }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>엑조디아"
        override val description = listOf(
            "<gray>패시브", "", "<gray>게임 시작 시, 월드보더 내부 무작위 위치에 왼쪽 팔, 오른쪽 팔, 왼쪽 다리, 오른쪽 다리, 몸통이 떨어진다.",
            "<gray>모두 모으면 자신을 제외한 모든 적은 {keyword:Execution}시킨다."
        )
    }

    companion object {
        private const val PART_LOCATION_ATTEMPTS = 100
        private const val PATH_MAX_VISITED_NODES = 12_000
        private const val FALLBACK_MAX_VISITED_NODES = 4_000
        private const val MINIMUM_PART_DISTANCE_SQUARED = 100.0
        private val WALK_DIRECTIONS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val WALKABLE_VERTICAL_OFFSETS = intArrayOf(1, 0, -1, -2, -3)
        private val UNSAFE_WALK_MATERIALS = setOf(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW,
            Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE,
        )
    }
}

private class ExodiaPartStatus : StatusAbnormality() {
    override val name = "<gold><bold>엑조디아 부위</bold><gray>"
    override val description = listOf("<gray>수집한 엑조디아 부위의 수이다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = 5
    override var duration: Int? = null
}
