package org.beobma.classWarPlugin.domain

import org.bukkit.World
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.pow

/** Native projectile motion does not use AbilityRunnable, so preserve and scale it explicitly. */
internal class DistortedProjectiles : AutoCloseable {
    private data class Motion(val projectile: Projectile, val gravity: Boolean, val velocity: Vector)
    private val motions = mutableMapOf<UUID, Motion>()
    fun tick(world: World, owners: Set<UUID>) {
        world.entities.filterIsInstance<Projectile>().filter {
            (it.shooter as? Player)?.uniqueId in owners && it.isValid && !it.isOnGround
        }.forEach { projectile ->
            val motion = motions.getOrPut(projectile.uniqueId) {
                Motion(projectile, projectile.hasGravity(), projectile.velocity.clone()).also { projectile.setGravity(false) }
            }
            val scale = 0.05
            projectile.velocity = motion.velocity.clone().multiply(scale)
            motion.velocity.multiply(0.99.pow(scale))
            if (motion.gravity) motion.velocity.y -= (if (projectile is AbstractArrow) 0.05 else 0.03) * scale
        }
        motions.values.filter { !it.projectile.isValid || it.projectile.isOnGround || (it.projectile.shooter as? Player)?.uniqueId !in owners }
            .toList().forEach { restore(it); motions.remove(it.projectile.uniqueId) }
    }
    private fun restore(motion: Motion) {
        if (!motion.projectile.isValid) return
        motion.projectile.setGravity(motion.gravity)
        if (!motion.projectile.isOnGround) motion.projectile.velocity = motion.velocity
    }
    override fun close() { motions.values.forEach(::restore); motions.clear() }
}
