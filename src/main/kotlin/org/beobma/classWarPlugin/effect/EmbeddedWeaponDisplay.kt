package org.beobma.classWarPlugin.effect

import org.beobma.classWarPlugin.ability.AbilityExecution
import org.beobma.classWarPlugin.ability.AbilityScope
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector

/** Ground contact and cosmetic model are kept separate so targeting uses the actual surface. */
internal class EmbeddedWeaponDisplay private constructor(
    val location: Location,
    private val display: ItemDisplay,
    private val scale: Float,
) : AutoCloseable {
    val isSupported: Boolean
        get() = display.isValid && !location.clone().subtract(0.0, 0.06, 0.0).block.isPassable

    fun move(position: Location, direction: Vector) {
        display.teleport(position.clone().apply { yaw = 0f; pitch = 0f })
        DisplayOrientationUtil.alignSwordBladeVertically(display, direction, scale)
    }

    override fun close() { display.remove() }

    companion object {
        fun spawn(scope: AbilityScope, requested: Location, material: Material, scale: Float): EmbeddedWeaponDisplay? {
            val surface = groundSurface(requested) ?: return null
            val center = surface.clone().add(0.0, scale * 0.42, 0.0).apply { yaw = 0f; pitch = 0f }
            val display = surface.world.spawn(center, ItemDisplay::class.java)
            AbilityExecution.with(scope) { TemporaryDisplayManager.mark(display, scope.playerData.uniqueId) }
            display.setItemStack(ItemStack(material))
            display.setGravity(false)
            display.teleportDuration = 1
            DisplayOrientationUtil.alignSwordBladeVertically(display, Vector(0.12, -1.0, 0.08), scale)
            return EmbeddedWeaponDisplay(surface, display, scale)
        }

        private fun groundSurface(requested: Location): Location? {
            val world = requested.world
            // Start just above the requested feet, permitting slab/stair tops and points against a wall.
            val origin = listOf(0.5, 1.0, 1.5).asSequence().map { requested.clone().add(0.0, it, 0.0) }
                .firstOrNull { it.y < world.maxHeight && it.block.isPassable } ?: return null
            val distance = (origin.y - world.minHeight).coerceAtLeast(0.0)
            if (distance == 0.0) return null
            val hit = world.rayTraceBlocks(origin, Vector(0.0, -1.0, 0.0), distance, FluidCollisionMode.NEVER, true)
                ?: return null
            if (hit.hitBlockFace != BlockFace.UP) return null
            return hit.hitPosition.toLocation(world).add(0.0, 0.02, 0.0)
        }
    }
}
