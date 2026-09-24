package org.beobma.classWarPlugin.gameClass.creator

import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

/** Shaft and gold ferrules sit behind the forward-facing blade, independent of camera rotation. */
internal class LightSpearModel(at: Location, owner: UUID) : AutoCloseable {
    private val parts = listOf(Material.END_ROD, Material.GOLD_BLOCK, Material.GOLD_BLOCK).map { material ->
        at.world.spawn(at.clone().apply { yaw = 0f; pitch = 0f }, BlockDisplay::class.java).apply {
            block = material.createBlockData(); brightness = Display.Brightness(15, 15)
            setGravity(false); teleportDuration = 1
            TemporaryDisplayManager.mark(this, owner)
        }
    }
    fun move(at: Location, direction: Vector) {
        if (direction.lengthSquared() < 1e-8) return
        val axis = direction.clone().normalize()
        val rotation = Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(axis.x.toFloat(), axis.y.toFloat(), axis.z.toFloat()))
        parts.forEachIndexed { index, part ->
            val width = if (index == 0) 0.9f else 0.18f
            val height = if (index == 0) 1.8f else 0.12f
            val back = when (index) { 0 -> 2.0; 1 -> 0.3; else -> 1.8 }
            part.transformation = Transformation(Vector3f(-width/2, 0f, -width/2).rotate(rotation), rotation,
                Vector3f(width, height, width), Quaternionf())
            part.teleport(at.clone().subtract(axis.clone().multiply(back)).apply { yaw = 0f; pitch = 0f })
        }
    }
    override fun close() { parts.forEach { it.remove() } }
}
