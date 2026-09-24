package org.beobma.classWarPlugin.gameClass.creator

import org.beobma.classWarPlugin.domain.*
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.util.Vector
import kotlin.math.*

/** DomainSession drives every frame, so cancellation cannot leave a looping soundtrack behind. */
internal class CreationPresentation(private val session: DomainSession) : DomainPresentation {
    private val base = DomainEffects(session)
    private val effects = CreationEffects(session.scope)
    private val center get() = session.center
    override fun start() { base.start() }
    override fun formation(progress: Double) { base.formation(progress) }
    override fun reveal(subtitle: Boolean) { base.reveal(subtitle) }
    override fun activate() {
        base.activate()
        repeat(8) { i ->
            val a = i*PI/4
            val column = center.clone().add(cos(a)*18, 13.5, sin(a)*18)
            effects.seal(column, 1.3, bright = true)
            ParticleApi.line(column, center.clone().add(0.0,19.0,0.0), Particle.END_ROD, spacing = 0.9)
        }
        SoundApi.play(center, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, volume = 0.8f, pitch = 0.55f)
    }
    override fun sustain(tick: Int) {
        if (tick % 6 != 0) return
        val a = tick*0.025
        val source = center.clone().add(0.0,19.0,0.0)
        effects.seal(source, 4.2, Vector(sin(a)*0.5,1.0,cos(a)*0.5), bright = true)
        // Only one tower glows each update: a travelling pulse instead of a full-screen particle fog.
        val tower = ((tick/6)%8)*PI/4
        effects.seal(center.clone().add(cos(tower)*18, 13.3, sin(tower)*18),1.5)
        if (tick % 60 == 0) SoundApi.play(source, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.35f, pitch = 0.65f)
    }
    override fun beginDissolve() {
        base.beginDissolve()
        SoundApi.play(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 0.65f, pitch = 0.65f)
    }
    override fun dissolve(tick: Int) {
        base.dissolve(tick)
        if (tick % 4 == 0) effects.seal(center.clone().add(0.0,19.0-tick*0.35,0.0), 5*(1-tick/40.0).coerceAtLeast(0.0), bright = true)
    }
}
