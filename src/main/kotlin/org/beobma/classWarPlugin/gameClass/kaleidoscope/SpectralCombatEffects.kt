package org.beobma.classWarPlugin.gameClass.kaleidoscope

import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.bukkit.*
import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.*

/** Visual timing and allocation never participate in damage or status calculations. */
internal object SpectralCombatVisuals {
    const val AFTERGLOW_TICKS = 14
    const val WAVE_BUDGET = 512
    const val MARK_TICKS = 12

    /** Rotate priority instead of permanently starving the last prisms in a large domain. */
    fun waveAllocation(count: Int, frame: Int): Map<Int, Int> {
        if (count <= 0) return emptyMap()
        val selected = min(count, 32)
        val samples = (WAVE_BUDGET / selected).coerceAtMost(64)
        return (0 until selected).associate { (Math.floorMod(frame, count) * selected + it) % count to samples }
    }

    fun rings(radius: Double, phase: Double, count: Int): List<Vector> = List(count) { i ->
        val plane = i % 3
        val angle = (i / 3) * 2 * PI / ceil(count / 3.0) + phase
        Vector(cos(angle)*radius, 0.0, sin(angle)*radius).rotateAroundX(plane*PI/3).rotateAroundY(phase*0.35)
    }

    fun beamStrength(age: Int): Double = if (age < 0 || age >= AFTERGLOW_TICKS) 0.0
        else if (age < 4) 1.0 else (AFTERGLOW_TICKS-age).toDouble()/(AFTERGLOW_TICKS-4)
}

internal class SpectralCombatEffects {
    private data class Mark(val entity: Entity, val born: Int, var power: Int, var burst: Boolean, var sounded: Boolean = false)
    private val marks = linkedMapOf<UUID, Mark>()
    fun clear() = marks.clear()

    private fun dust(at: Location, color: Int, size: Float = 1.5f, white: Boolean = false) =
        SpectralFx.dust(at, color, size, white)

    fun transfer(from: Location, to: Location, progress: Double, color: Int) {
        if(from.world!=to.world) return
        val delta=to.toVector().subtract(from.toVector())
        repeat(8) { i ->
            val t=(progress-i*0.045).coerceIn(0.0,1.0)
            dust(from.clone().add(delta.clone().multiply(t)),color+i/3,if(i==0) 1.8f else 1.0f,i<2)
        }
    }

    fun prismShatter(at: Location, color: Int) {
        repeat(6) { arm ->
            val angle=arm*PI/3
            repeat(4) { i -> dust(at.clone().add(cos(angle)*i*0.12,i*0.08,sin(angle)*i*0.12),color+arm,0.85f,i==0) }
        }
        ParticleApi.spawn(at,Particle.END_ROD,count=5,spread=0.12,speed=0.025)
    }

    fun warning(at: Location, top: Location, age: Int) {
        if (age % 2 != 0) return
        val progress = age.toDouble()/SpectralGeometry.WARNING_TICKS
        val r = 1.65-progress*0.8
        repeat(48) { i ->
            val angle=i*PI/24+age*0.08
            dust(at.clone().add(cos(angle)*r,0.08,sin(angle)*r),i/8,1.35f,i%8==0)
        }
        // An overhead iris and six converging rays explain where the strike is coming from.
        val head = top.clone().apply { y -= (top.y-at.y)*((progress-0.55)/0.45).coerceIn(0.0,1.0) }
        repeat(6) { arm ->
            val angle = arm*PI/3-age*0.12
            repeat(5) { j ->
                val rayRadius = (0.4 + j*0.3)*(1-progress*0.4)
                dust(head.clone().add(cos(angle)*rayRadius,0.0,sin(angle)*rayRadius),arm,1.5f,j==0)
            }
        }
        if(age%4==0) SoundApi.play(at,Sound.BLOCK_NOTE_BLOCK_CHIME,0.35f,(0.7+progress*1.1).toFloat())
    }

    fun beam(at: Location, top: Location, age: Int) {
        val strength = SpectralCombatVisuals.beamStrength(age)
        if (strength <= 0 || (age>=4 && age%2!=0)) return
        val samples=ceil((top.y-at.y)/0.32).toInt().coerceIn(2,80)
        repeat(samples+1) { i ->
            val y=(top.y-at.y)*i/samples
            val core=at.clone().add(0.0,y,0.0)
            dust(core,0,(1.2+1.3*strength).toFloat(),true)
            val angle=y*0.8+age*0.3
            for(side in listOf(-1,1)) dust(core.clone().add(cos(angle)*0.43*side,0.0,sin(angle)*0.43*side),i/6+age+side,
                (0.8+0.9*strength).toFloat())
            if(age==0 && i%3==0) ParticleApi.spawn(core,Particle.END_ROD,count=1,speed=0.0)
        }
        val radius=0.8+age*0.12
        repeat(36) { i ->
            val a=i*PI/18
            dust(at.clone().add(cos(a)*radius,0.12,sin(a)*radius),i/6+age/2,(0.6+strength).toFloat(),i%6==0)
        }
    }

    fun impact(at: Location) {
        ParticleApi.spawn(at,Particle.EXPLOSION,count=1)
        ParticleApi.spawn(at,Particle.END_ROD,count=32,spread=0.3,speed=0.22)
        ParticleApi.spawn(at,Particle.ELECTRIC_SPARK,count=24,spread=0.65,speed=0.12)
        repeat(6) { arm ->
            val a=arm*PI/3
            repeat(12) { j ->
                val r=0.18+j*0.2
                dust(at.clone().add(cos(a)*r,0.15+sin(j*PI/11)*0.55,sin(a)*r),arm,1.6f,j<3)
            }
        }
        SoundApi.play(at,Sound.ENTITY_LIGHTNING_BOLT_IMPACT,0.65f,1.35f)
        SoundApi.play(at,Sound.BLOCK_AMETHYST_BLOCK_BREAK,0.85f,0.8f)
        SoundApi.play(at,Sound.BLOCK_BEACON_POWER_SELECT,0.65f,0.65f)
    }

    fun wave(at: Location, radius: Double, phase: Int, samples: Int = 64) {
        SpectralCombatVisuals.rings(radius,phase*0.05,samples).forEachIndexed { i,p ->
            dust(at.clone().add(p),i/6+phase/5,if(i%6==0) 1.9f else 1.4f,i%6==0)
        }
    }

    /** Multiple domain waves striking one entity in one tick share one readable impact marker. */
    fun brightness(entity: Entity, power: Int, burst: Boolean, tick: Int) {
        val old=marks[entity.uniqueId]
        if(old==null || tick-old.born>=SpectralCombatVisuals.MARK_TICKS)
            marks[entity.uniqueId]=Mark(entity,tick,power,burst)
        else {
            old.power=max(old.power,power)
            if (burst && !old.burst) old.sounded=false
            old.burst=old.burst || burst
        }
    }

    fun tick(tick: Int) {
        marks.entries.removeAll { !it.value.entity.isValid || it.value.entity.isDead || tick-it.value.born>=SpectralCombatVisuals.MARK_TICKS }
        val active=marks.values.toList()
        // A second independent budget ensures passive hits cannot be hidden behind wave rendering.
        val selection=SpectralCombatVisuals.waveAllocation(active.size,tick)
        selection.forEach { (index,budget) ->
            val mark=active[index]
            val at=mark.entity.boundingBox.center.toLocation(mark.entity.world)
            val age=tick-mark.born
            if(!mark.sounded) {
                mark.sounded=true
                SoundApi.play(at,if(mark.burst) Sound.BLOCK_AMETHYST_BLOCK_BREAK else Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    if(mark.burst) 0.65f else 0.38f,if(mark.burst) 0.7f else (0.9+mark.power.coerceIn(1,5)*0.12).toFloat())
                ParticleApi.spawn(at,Particle.END_ROD,count=if(mark.burst) 12 else 5,spread=0.25,speed=if(mark.burst) 0.12 else 0.035)
            }
            if(age%2!=0) return@forEach
            val radius=(max(mark.entity.boundingBox.widthX,mark.entity.boundingBox.widthZ)*0.55+0.32).coerceAtMost(2.4)+age*0.035
            val count=min(budget,48)
            repeat(count) { i ->
                val angle=i*2*PI/count+age*0.12
                // A crystalline crown surrounds the target, with a sharp six-point burst at five stacks.
                val r=radius*(if(mark.burst && i%2==0) 1.4 else 1.0)
                dust(at.clone().add(cos(angle)*r,sin(angle*3+age*0.15)*0.45,sin(angle)*r),i/6+mark.power,
                    if(mark.burst) 1.8f else 1.3f,i%8==0)
            }
        }
    }
}
