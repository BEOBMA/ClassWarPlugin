package org.beobma.classWarPlugin.domain

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import kotlin.math.*

/** Fixed-size geometry keeps the particle budget independent of domain volume and player count. */
internal object DomainEffectGeometry {
    data class Point(val x: Double, val y: Double, val z: Double)

    fun ring(radius: Double, height: Double, phase: Double = 0.0, count: Int = 64): List<Point> =
        List(count) { i ->
            val angle = phase + i * 2 * PI / count
            Point(cos(angle) * radius, height, sin(angle) * radius)
        }

    fun crown(radius: Double, progress: Double, phase: Double): List<Point> {
        val height = radius * progress.coerceIn(0.0, 1.0)
        val width = sqrt((radius * radius - height * height).coerceAtLeast(0.0))
        return ring(width, height, phase)
    }

    fun meridians(radius: Double, phase: Double): List<Point> = (0 until 6).flatMap { arm ->
        List(12) { step ->
            val elevation = step / 12.0 * PI / 2
            val angle = arm * PI / 3 + phase + elevation * 0.65
            Point(cos(angle) * cos(elevation) * radius, sin(elevation) * radius,
                sin(angle) * cos(elevation) * radius)
        }
    }
}

/** Driven only by DomainSession: no independent timers or looping sounds can outlive the domain. */
internal class DomainEffects(private val session: DomainSession) : DomainPresentation {
    private val center get() = session.center
    // Keep the glow in front of the inner faces of the opaque, voxel-shaped shell.
    private val radius = session.definition.radius.toDouble() - 1.8
    private val violet = Particle.DustOptions(Color.fromRGB(142, 65, 230), 1.25f)
    private val gold = Particle.DustOptions(Color.fromRGB(255, 206, 96), 1.1f)
    private val ice = Particle.DustOptions(Color.fromRGB(155, 232, 255), 0.9f)
    private var introductionTicks = 0

    private fun at(point: DomainEffectGeometry.Point) = center.clone().add(point.x, point.y, point.z)
    private fun dust(points: List<DomainEffectGeometry.Point>, color: Particle.DustOptions) {
        points.forEach { center.world.spawnParticle(Particle.DUST, at(it), 1, 0.0, 0.0, 0.0, 0.0, color) }
    }
    private fun burst(location: Location, particle: Particle, count: Int, spread: Double, speed: Double = 0.0) {
        center.world.spawnParticle(particle, location, count, spread, spread * 0.45, spread, speed)
    }
    private fun sound(sound: Sound, volume: Float, pitch: Float) {
        val reach = session.definition.radius + 12.0
        center.world.players.filter {
            it.uniqueId in session.participants || it.location.distanceSquared(center) <= reach * reach
        }.forEach { it.playSound(it.location, sound, SoundCategory.PLAYERS, volume, pitch) }
    }

    override fun start() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0.65f, 0.55f)
        sound(Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.7f, 0.65f)
        burst(center.clone().add(0.0, 0.4, 0.0), Particle.REVERSE_PORTAL, 55, 1.0, 0.08)
        dust(DomainEffectGeometry.ring(1.2, 0.15), gold)
    }

    override fun formation(progress: Double) {
        val tick = introductionTicks++
        if (tick % 2 != 0) return
        val p = progress.coerceIn(0.0, 1.0)
        val phase = tick * 0.045
        val expanding = radius * (1 - (1 - p).pow(3)).coerceAtLeast(0.08)
        dust(DomainEffectGeometry.ring(expanding, 0.12, phase), gold)
        dust(DomainEffectGeometry.ring(expanding * 0.82, 0.16, -phase, 48), violet)
        dust(DomainEffectGeometry.crown(radius, p * 0.96, phase), ice)
        dust(DomainEffectGeometry.meridians(radius, phase * 0.3).filter { it.y <= p * radius }, violet)
        if (tick % 10 == 0) {
            sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.32f, (0.55 + p * 1.2).toFloat())
            burst(center.clone().add(0.0, 1.0 + p * 2, 0.0), Particle.ENCHANT, 16, 1.5)
        }
    }

    override fun reveal(subtitle: Boolean) {
        // Domain activation has no title reveal; its formation effects are handled separately.
    }

    override fun activate() {
        sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.55f, 1.4f)
        dust(DomainEffectGeometry.ring(radius * 0.6, 0.15), gold)
        burst(center.clone().add(0.0, radius * 0.8, 0.0), Particle.END_ROD, 24, 1.5, 0.015)
    }

    override fun sustain(tick: Int) {
        if (tick % 4 != 0) return
        val phase = tick * 0.009
        dust(DomainEffectGeometry.ring(radius, 0.14, phase), violet)
        dust(DomainEffectGeometry.meridians(radius, -phase * 0.6), ice)
        // Broken inner seal and rotating twelve-point runes leave the central combat area readable.
        val inner = DomainEffectGeometry.ring(radius * 0.82, 0.17, -phase, 48)
        dust(inner.filterIndexed { index, _ -> index % 4 != 3 }, gold)
        DomainEffectGeometry.ring(radius * 0.88, 0.3, phase, 12).forEach {
            burst(at(it), Particle.ENCHANT, 1, 0.04)
        }
        if (tick % 80 == 0) sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.23f, 0.65f)
    }

    override fun beginDissolve() {
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.65f, 0.6f)
        sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.6f, 0.7f)
        burst(center.clone().add(0.0, 1.0, 0.0), Particle.REVERSE_PORTAL, 45, 2.0, 0.08)
    }

    override fun dissolve(tick: Int) {
        if (tick % 2 != 0) return
        val remaining = (1 - tick / 40.0).coerceIn(0.0, 1.0)
        dust(DomainEffectGeometry.ring(radius * remaining, 0.2, tick * 0.08), gold)
        dust(DomainEffectGeometry.crown(radius, remaining, tick * -0.04), violet)
        DomainEffectGeometry.crown(radius, remaining, tick * 0.025).filterIndexed { index, _ -> index % 4 == 0 }.forEach {
            burst(at(it), Particle.ASH, 2, 0.2)
        }
        if (tick == 40) {
            sound(Sound.BLOCK_GLASS_BREAK, 0.6f, 0.65f)
            sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 0.8f)
            burst(center.clone().add(0.0, 0.5, 0.0), Particle.CLOUD, 30, 1.8, 0.025)
        }
    }
}
