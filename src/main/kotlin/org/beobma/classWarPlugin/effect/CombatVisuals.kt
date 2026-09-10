package org.beobma.classWarPlugin.effect

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.AbilityRunnable
import org.beobma.classWarPlugin.ability.AbilityScope
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Bounded, cosmetic geometry. Never performs targeting, damage or terrain changes. */
internal object CombatVisuals {
    val SILVER: Color = Color.fromRGB(210, 238, 255)
    val GOLD: Color = Color.fromRGB(255, 188, 65)
    val VIOLET: Color = Color.fromRGB(178, 80, 235)
    val CYAN: Color = Color.fromRGB(70, 225, 255)

    // A local plane independent of viewer yaw; vertical shots retain a valid basis.
    fun plane(normal: Vector): Pair<Vector, Vector> {
        val forward = if (normal.lengthSquared() < 1.0E-8) Vector(0.0, 0.0, 1.0) else normal.clone().normalize()
        val reference = if (kotlin.math.abs(forward.y) > 0.98) Vector(0.0, 0.0, 1.0) else Vector(0.0, 1.0, 0.0)
        val right = reference.crossProduct(forward).normalize()
        return right to forward.crossProduct(right).normalize()
    }

    fun ring(center: Location, normal: Vector, radius: Double, color: Color, points: Int = 28) {
        val (right, up) = plane(normal)
        val dust = Particle.DustOptions(color, 0.95f)
        val count = points.coerceIn(8, 64)
        repeat(count) { index ->
            val angle = 2 * PI * index / count
            ParticleApi.spawn(center.clone().add(right.clone().multiply(cos(angle) * radius))
                .add(up.clone().multiply(sin(angle) * radius)), Particle.DUST, dust)
        }
    }

    fun pulse(scope: AbilityScope, center: Location, normal: Vector, radius: Double, color: Color) {
        val origin = center.clone()
        val axis = normal.clone()
        ring(origin, axis, radius * 0.25, color)
        object : AbilityRunnable(scope) {
            var frame = 0
            override fun run() {
                ring(origin, axis, radius * (0.4 + frame * 0.15), color)
                if (++frame >= 5) cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 2L)
    }

    fun tracer(from: Location, to: Location, color: Color, spiral: Boolean = false) {
        if (from.world != to.world) return
        val delta = to.toVector().subtract(from.toVector())
        val length = delta.length()
        if (length < 0.01) return
        val count = kotlin.math.ceil(length / 0.3).toInt().coerceIn(2, 96)
        val (right, up) = plane(delta)
        val dust = Particle.DustOptions(color, if (spiral) 1.15f else 0.85f)
        repeat(count + 1) { index ->
            val t = index.toDouble() / count
            val point = from.clone().add(delta.clone().multiply(t))
            ParticleApi.spawn(point, Particle.DUST, dust)
            if (spiral && index % 2 == 0) {
                val angle = t * length * 2.4
                val radius = 0.17 * sin(PI * t)
                ParticleApi.spawn(point.add(right.clone().multiply(cos(angle) * radius))
                    .add(up.clone().multiply(sin(angle) * radius)), Particle.SOUL_FIRE_FLAME)
            }
        }
    }

    /** Sweep advances through four segments; only the visual is delayed, never the hit. */
    fun slash(scope: AbilityScope, center: Location, direction: Vector, radius: Double,
              color: Color, tilt: Double = 0.5, reverse: Boolean = false) {
        val forward = if (direction.lengthSquared() < 1.0E-8) Vector(0.0, 0.0, 1.0) else direction.clone().normalize()
        val (right, up) = plane(forward)
        val lateral = right.multiply(cos(tilt)).add(up.multiply(sin(tilt)))
        val origin = center.clone()
        val dust = Particle.DustOptions(color, 1.1f)
        val core = Particle.DustOptions(SILVER, 0.65f)
        fun draw(segment: Int) {
            repeat(8) { sample ->
                val t = (segment * 8 + sample) / 31.0
                val angle = (-65.0 + 130.0 * if (reverse) 1.0 - t else t) * PI / 180.0
                val offset = forward.clone().multiply(cos(angle)).add(lateral.clone().multiply(sin(angle)))
                ParticleApi.spawn(origin.clone().add(offset.clone().multiply(radius)), Particle.DUST, dust)
                ParticleApi.spawn(origin.clone().add(offset.multiply(radius * 0.91)), Particle.DUST, core)
            }
        }
        draw(0)
        object : AbilityRunnable(scope) {
            var segment = 1
            override fun run() {
                draw(segment)
                if (++segment >= 4) cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }
}
