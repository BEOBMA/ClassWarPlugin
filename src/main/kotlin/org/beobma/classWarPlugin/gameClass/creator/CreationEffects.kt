package org.beobma.classWarPlugin.gameClass.creator

import org.beobma.classWarPlugin.ability.AbilityScope
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.bukkit.*
import org.bukkit.util.Vector
import kotlin.math.*

/** Fixed budgets; all pulses advance on the owning runtime's combat task. */
internal class CreationEffects(private val scope: AbilityScope) {
    private data class Pulse(val at: Location, val radius: Double, val chain: Boolean, var age: Int = 0)
    private val pulses = ArrayDeque<Pulse>()
    private val nextSound = mutableMapOf<String, Long>()
    private val gold = Particle.DustOptions(Color.fromRGB(255, 194, 72), 1.0f)
    private val white = Particle.DustOptions(Color.fromRGB(225, 247, 255), 0.8f)

    fun seal(at: Location, radius: Double, normal: Vector = Vector(0.0, 1.0, 0.0), bright: Boolean = false) {
        CreationGeometry.seal(radius, normal).forEach { offset ->
            ParticleApi.spawn(at.clone().add(offset), Particle.DUST, if (bright) white else gold)
        }
    }
    private fun sound(key: String, at: Location, type: Sound, volume: Float, pitch: Float, spacing: Long = 5) {
        val now = scope.game.combatTick
        if (now < (nextSound[key] ?: Long.MIN_VALUE)) return
        nextSound[key] = now + spacing
        SoundApi.play(at, type, volume = volume, pitch = pitch)
    }
    fun summon(at: Location, chain: Boolean, direction: Vector) {
        seal(at, if (chain) 1.0 else 0.65, direction)
        sound("summon", at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45f, if (chain) 0.65f else 1.4f)
    }
    fun impact(at: Location, chain: Boolean) {
        seal(at.clone().add(0.0, 0.08, 0.0), if (chain) 0.85 else 0.55, bright = !chain)
        if (chain) {
            sound("heavy", at, Sound.BLOCK_HEAVY_CORE_PLACE, 0.95f, 0.55f)
            sound("metal", at, Sound.ENTITY_IRON_GOLEM_HURT, 0.6f, 0.6f)
            ParticleApi.spawn(at, Particle.CLOUD, count = 10, spread = 0.45, speed = 0.025)
        } else {
            sound("spear", at, Sound.ITEM_TRIDENT_HIT_GROUND, 0.6f, 0.8f)
            sound("chime", at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.5f)
            ParticleApi.spawn(at, Particle.ELECTRIC_SPARK, count = 12, spread = 0.25, speed = 0.06)
        }
    }
    fun shatter(at: Location, chain: Boolean) {
        if (pulses.size >= 8) pulses.removeFirst()
        pulses.addLast(Pulse(at.clone(), if (chain) 2.0 else 3.0, chain))
        sound("shatter", at, if (chain) Sound.BLOCK_CHAIN_BREAK else Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 0.65f)
        ParticleApi.spawn(at, if (chain) Particle.CRIT else Particle.FIREWORK, count = 18, spread = 0.6, speed = 0.1)
    }
    fun tick() {
        val iterator = pulses.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (++p.age > 8) { iterator.remove(); continue }
            if (p.age % 2 == 0) seal(p.at.clone().add(0.0, 0.08 + p.age*0.035, 0.0), p.radius*p.age/8, bright = !p.chain)
        }
    }
    fun clear() { pulses.clear(); nextSound.clear() }
}
