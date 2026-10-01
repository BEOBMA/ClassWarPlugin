package org.beobma.classWarPlugin.gameClass.constellations

import org.beobma.classWarPlugin.domain.*
import org.beobma.classWarPlugin.effect.ParticleApi
import org.bukkit.*
import kotlin.math.*

/** Full-sphere star field is visible through the invisible barrier floor. No independent sound tasks. */
internal class ConstellationPresentation(private val session: DomainSession) : DomainPresentation {
    private val base = DomainEffects(session)
    private val center get() = session.center
    private val stars = List(240) { i ->
        val y = 1 - 2*(i+0.5)/240.0
        val angle = i*PI*(3-sqrt(5.0))
        val r = sqrt(1-y*y)*23.0
        center.clone().add(cos(angle)*r,y*23.0,sin(angle)*r)
    }
    private var formationTick = 0
    private val violet = Particle.DustOptions(Color.fromRGB(98, 46, 168), 2.0f)
    private val blue = Particle.DustOptions(Color.fromRGB(51, 93, 161), 1.8f)
    private val white = Particle.DustOptions(Color.fromRGB(204, 219, 255), 0.8f)

    private fun sound(sound: Sound, volume: Float, pitch: Float) {
        session.players().forEach { it.playSound(it.location, sound, SoundCategory.PLAYERS, volume, pitch) }
    }
    private fun dust(point: Location, options: Particle.DustOptions) {
        point.world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, options)
    }
    override fun start() {
        base.start()
        sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.65f, 0.5f)
    }
    override fun formation(progress: Double) {
        base.formation(progress)
        if (formationTick++ % 3 == 0) {
            val scale = progress.coerceIn(0.05, 1.0)
            stars.filterIndexed { i, _ -> i % 3 == formationTick % 3 }.forEach {
                dust(center.clone().add(it.toVector().subtract(center.toVector()).multiply(scale)), white)
            }
            nebula(formationTick, scale)
        }
    }
    override fun reveal(subtitle: Boolean) = base.reveal(subtitle)
    override fun activate() {
        base.activate()
        sound(Sound.BLOCK_END_PORTAL_SPAWN,0.45f,0.5f)
        sound(Sound.BLOCK_BEACON_ACTIVATE,0.4f,0.5f)
        field(0)
    }

    // A tilted galactic belt and two spirals above/below the arena keep the combat plane readable.
    // Fixed sample counts bound the cost independently of radius and number of participants.
    private fun nebula(tick: Int, scale: Double = 1.0) {
        repeat(120) { i ->
            val angle = i * 2 * PI / 120 + tick * 0.0015
            val p = org.bukkit.util.Vector(cos(angle)*22, sin(i*2.4)*0.9, sin(angle)*22)
                .rotateAroundX(0.62).multiply(scale)
            dust(center.clone().add(p), if (i % 3 == 0) violet else blue)
        }
        for (side in listOf(-1, 1)) repeat(72) { i ->
            val t = i / 71.0
            val angle = t*PI*4 + tick*0.012*side
            val radius = (2 + t*12)*scale
            dust(center.clone().add(cos(angle)*radius, side*(15-t*3)*scale, sin(angle)*radius),
                if (i % 4 == 0) white else violet)
        }
    }

    private fun meteor(tick: Int) {
        val phase = tick % 40
        val angle = (tick / 40) * 2.39996
        val origin = center.clone().add(cos(angle)*14, 16.0, sin(angle)*14)
        val direction = org.bukkit.util.Vector(-sin(angle), -0.25, cos(angle)).normalize()
        val head = origin.add(direction.clone().multiply(phase*0.3))
        repeat(8) { i -> dust(head.clone().subtract(direction.clone().multiply(i*0.24)), if (i < 2) white else blue) }
        if (phase == 0) sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.18f,1.7f)
    }
    private fun field(tick: Int) {
        stars.forEachIndexed { i, point ->
            val color = if ((i+tick/8)%5 == 0) Color.fromRGB(255,218,135) else Color.fromRGB(167,184,255)
            point.world.spawnParticle(Particle.DUST,point,1,0.0,0.0,0.0,0.0,Particle.DustOptions(color,1.25f))
        }
        if (tick%24 == 0) for (i in 0..7) {
            val index = (tick/24*7+i*11)%stars.size
            val nearest = stars.indices.filter { it != index }.sortedBy { stars[it].distanceSquared(stars[index]) }.take(2)
            nearest.forEach { ParticleApi.line(stars[index],stars[it],Particle.END_ROD,spacing=0.9) }
        }
    }
    override fun sustain(tick: Int) {
        if (tick%6 == 0) field(tick)
        if (tick%8 == 0) nebula(tick)
        if (tick%2 == 0) meteor(tick)
        if (tick%80 == 0) {
            sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE,0.3f,0.5f)
            sound(Sound.BLOCK_CONDUIT_AMBIENT,0.22f,0.5f)
        }
    }
    override fun beginDissolve() {
        base.beginDissolve()
        sound(Sound.BLOCK_CONDUIT_DEACTIVATE,0.65f,0.5f)
    }
    override fun dissolve(tick: Int) {
        base.dissolve(tick)
        if (tick%4 == 0) {
            nebula(tick, (1-tick/40.0).coerceAtLeast(0.0))
            stars.filterIndexed { i,_ -> i%8 == tick/4%8 }.forEach {
                val delta = it.toVector().subtract(center.toVector())
                val head = center.clone().add(delta.clone().multiply((1-tick/40.0).coerceAtLeast(0.0)))
                ParticleApi.line(head,head.clone().add(delta.normalize().multiply(1.2)),Particle.END_ROD,spacing=0.4)
            }
        }
    }
}
