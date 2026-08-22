package org.beobma.classWarPlugin.effect

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Color
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class ParticleOptions(
    val count: Int = 1,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.0,
    val force: Boolean = false,
) {
    companion object {
        fun spread(
            count: Int,
            spread: Double,
            speed: Double = 0.0,
            force: Boolean = false,
        ) = ParticleOptions(count, spread, spread, spread, speed, force)
    }
}

/** 클래스 스킬의 파티클 출력을 담당하는 API. */
object ParticleApi {
    fun spawn(
        location: Location,
        particle: Particle,
        count: Int = 1,
        spread: Double = 0.0,
        speed: Double = 0.0,
        force: Boolean = false,
    ) = spawn(location, particle, ParticleOptions.spread(count, spread, speed, force))

    fun spawn(location: Location, particle: Particle, options: ParticleOptions) {
        val data = defaultData(particle)
        location.world.spawnParticle(
            particle,
            location,
            options.count,
            options.offsetX,
            options.offsetY,
            options.offsetZ,
            options.speed,
            data,
            options.force,
        )
    }

    fun <T : Any> spawn(
        location: Location,
        particle: Particle,
        data: T,
        options: ParticleOptions = ParticleOptions(),
    ) {
        location.world.spawnParticle(
            particle,
            location,
            options.count,
            options.offsetX,
            options.offsetY,
            options.offsetZ,
            options.speed,
            data,
            options.force,
        )
    }

    fun spawn(
        entity: Entity,
        particle: Particle,
        count: Int = 1,
        spread: Double = 0.0,
        speed: Double = 0.0,
        heightOffset: Double = entity.height / 2.0,
        force: Boolean = false,
    ) = spawn(entity.location.add(0.0, heightOffset, 0.0), particle, count, spread, speed, force)

    fun spawnTo(
        player: Player,
        location: Location,
        particle: Particle,
        count: Int = 1,
        spread: Double = 0.0,
        speed: Double = 0.0,
    ) {
        player.spawnParticle(particle, location, count, spread, spread, spread, speed, defaultData(particle))
    }

    fun line(
        from: Location,
        to: Location,
        particle: Particle,
        spacing: Double = 0.25,
        options: ParticleOptions = ParticleOptions(),
    ) {
        require(from.world == to.world) { "Particle line locations must be in the same world." }
        require(spacing > 0.0) { "Particle spacing must be positive." }

        val difference = to.toVector().subtract(from.toVector())
        val distance = difference.length()
        val points = max(1, kotlin.math.ceil(distance / spacing).toInt())
        val step = difference.multiply(1.0 / points)
        val current = from.clone()
        repeat(points + 1) {
            spawn(current, particle, options)
            current.add(step)
        }
    }

    fun circle(
        center: Location,
        particle: Particle,
        radius: Double,
        points: Int = 24,
        options: ParticleOptions = ParticleOptions(),
    ) {
        require(radius >= 0.0) { "Particle circle radius must be non-negative." }
        require(points > 0) { "Particle circle points must be positive." }

        repeat(points) { index ->
            val angle = 2.0 * PI * index / points
            val location = center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius)
            spawn(location, particle, options)
        }
    }

    /** Paper 26.2에서 일부 파티클(END_ROD 등)에 새로 요구되는 기본 데이터를 제공한다. */
    private fun defaultData(particle: Particle): Any? = when (particle.dataType) {
        Color::class.java -> Color.WHITE
        else -> null
    }
}
