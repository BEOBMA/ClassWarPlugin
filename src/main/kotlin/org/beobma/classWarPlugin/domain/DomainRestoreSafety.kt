package org.beobma.classWarPlugin.domain

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockState
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import kotlin.math.abs
import kotlin.math.floor

/** Conservative full-cell checks for future terrain, actual player boxes for every relocation. */
internal object DomainRestoreSafety {
    private val hazards = setOf(Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
        Material.CACTUS, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE)

    fun obstructs(material: Material) = material.isSolid || material in hazards

    fun overlaps(box: BoundingBox, x: Int, y: Int, z: Int): Boolean =
        box.overlaps(BoundingBox(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0))

    fun findDestination(
        player: Player, original: Location?, borderCenter: Location, borderSize: Double,
        snapshot: (Triple<Int, Int, Int>) -> BlockState?,
    ): Location? {
        val world = player.world
        val source = player.location
        val currentBox = player.boundingBox
        val half = borderSize / 2.0
        fun fitsBorder(box: BoundingBox) = box.minX >= borderCenter.x - half && box.maxX <= borderCenter.x + half &&
            box.minZ >= borderCenter.z - half && box.maxZ <= borderCenter.z + half
        fun future(x: Int, y: Int, z: Int) = snapshot(Triple(x, y, z))?.type ?: world.getBlockAt(x, y, z).type
        fun safe(at: Location): Boolean {
            val box = currentBox.clone().shift(at.toVector().subtract(source.toVector()))
            if (box.minY < world.minHeight + 1 || box.maxY >= world.maxHeight || !fitsBorder(box)) return false
            for (x in floor(box.minX).toInt()..floor(box.maxX - 1e-7).toInt())
                for (y in floor(box.minY).toInt()..floor(box.maxY - 1e-7).toInt())
                    for (z in floor(box.minZ).toInt()..floor(box.maxZ - 1e-7).toInt()) {
                        val block = world.getBlockAt(x, y, z)
                        if (!block.isPassable || block.type in hazards || obstructs(future(x, y, z))) return false
                    }
            val ground = world.getBlockAt(at.blockX, floor(box.minY - 0.01).toInt(), at.blockZ)
            return ground.type.isSolid && ground.type !in hazards &&
                abs(ground.boundingBox.maxY - box.minY) < 0.02 &&
                future(ground.x, ground.y, ground.z).let { it.isSolid && it !in hazards }
        }
        val bases = listOfNotNull(original?.takeIf { it.world == world }, source, world.spawnLocation)
        for (base in bases) {
            if (safe(base)) return base.clone().apply { yaw = source.yaw; pitch = source.pitch }
            for (radius in 0..12) for (dx in -radius..radius) for (dz in -radius..radius) {
                if (maxOf(abs(dx), abs(dz)) != radius) continue
                val x = base.blockX + dx + 0.5
                val z = base.blockZ + dz + 0.5
                if (x < borderCenter.x - half || x > borderCenter.x + half ||
                    z < borderCenter.z - half || z > borderCenter.z + half) continue
                val heights = sequence {
                    for (offset in 0..8) {
                        yield(base.blockY + offset)
                        if (offset != 0) yield(base.blockY - offset)
                    }
                    yield(world.getHighestBlockYAt(floor(x).toInt(), floor(z).toInt()) + 1)
                }
                for (y in heights) {
                    val at = Location(world, x, y.toDouble(), z, source.yaw, source.pitch)
                    if (safe(at)) return at
                }
            }
        }
        return null
    }
}
