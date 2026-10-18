package org.beobma.classWarPlugin.gameClass.kaleidoscope

import org.beobma.classWarPlugin.domain.*
import org.bukkit.*
import org.bukkit.util.Vector
import kotlin.math.*

internal object SpectralFx {
    private val rgb = listOf(0xFF5757,0xFF9E45,0xFFE36B,0x62E899,0x59D9FF,0x7287FF,0xCC83FF)
    private val colors = rgb.map(Color::fromRGB)
    fun hex(index: Int) = "#%06x".format(rgb[Math.floorMod(index,7)])
    fun dust(at: Location, index: Int, size: Float, white: Boolean = false) {
        org.beobma.classWarPlugin.effect.ParticleApi.spawn(at, Particle.DUST,
            Particle.DustOptions(if (white) Color.fromRGB(240,247,255) else colors[Math.floorMod(index,7)],size))
    }
}

/** An animated, mirrored optical chamber. No extra schedulers: all sound stops with the domain. */
internal class KaleidoscopePresentation(private val session: DomainSession) : DomainPresentation {
    private val center get() = session.center
    private var frame = 0
    private fun iris(phase: Double, radius: Double, height: Double) {
        // Six identical triangular optical paths, reflected across each sector's center line.
        repeat(6) { sector ->
            for(side in listOf(-1,1)) {
                val a=sector*PI/3+phase
                val start=Vector(cos(a)*radius*0.3,height,sin(a)*radius*0.3)
                val tip=Vector(cos(a+side*0.38)*radius,height,sin(a+side*0.38)*radius)
                val end=Vector(cos(a+side*0.12)*radius*0.58,height,sin(a+side*0.12)*radius*0.58)
                for((from,to) in listOf(start to tip,tip to end)) repeat(4) { i ->
                    point(from.clone().multiply(1-i/4.0).add(to.clone().multiply(i/4.0)),sector,0.9f,i==0)
                }
            }
        }
    }
    private fun sound(sound: Sound, volume: Float, pitch: Float) {
        session.players().forEach { it.playSound(it.location, sound, SoundCategory.PLAYERS, volume, pitch) }
    }
    private fun point(p: Vector, color: Int, size: Float = 1.05f, white: Boolean = false) = SpectralFx.dust(center.clone().add(p),color,size,white)

    override fun start() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 0.5f)
        sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.75f, 0.65f)
        motif(0,0.08)
    }
    override fun formation(progress: Double) {
        frame++
        if (frame%3==0) motif(frame, 0.1+progress*0.9)
        if (frame%12==0) sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.4f,(0.55+progress*1.25).toFloat())
        if (frame%6==0) iris(frame*0.015,1.5+progress*8,1.0+progress*11)
        // Six mirrored planes fold up from the caster into the dome.
        if (frame%4==0) repeat(12) { arm ->
            val angle = arm*PI/6
            repeat(10) { j ->
                val elevation = progress*PI/2*j/9
                val r = 18.0*cos(elevation)*progress
                point(Vector(cos(angle)*r,18.0*sin(elevation)*progress,sin(angle)*r),arm+j,1.3f)
            }
        }
    }
    override fun reveal(subtitle: Boolean) = Unit
    override fun activate() {
        sound(Sound.BLOCK_END_PORTAL_SPAWN,0.45f,0.55f)
        sound(Sound.BLOCK_GLASS_BREAK,0.7f,0.65f)
        sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.8f,1.2f)
        motif(0,1.0)
        repeat(12) { arm ->
            val angle=arm*PI/6
            repeat(16) { j -> point(Vector(cos(angle)*j,0.2,sin(angle)*j),arm,1.3f,j%3==0) }
        }
    }

    private fun motif(tick: Int, scale: Double) {
        // Counter-rotating nested six-petal rosettes retain mirror symmetry as their hues cycle.
        val phase=tick*0.009
        for (layer in 0..2) {
            val r=(17.5-layer*3.5)*scale
            val height = if (layer==0) 0.18 else (8.0+layer*2.4)*scale
            SpectralGeometry.rosette(r,height,phase*(if(layer%2==0) 1 else -1),96).forEachIndexed { i,p ->
                point(p,i/8+tick/16+layer*2,if(layer==0) 0.85f else 1.3f)
            }
        }
        // Repeated diamond facets sit against the inner shell, leaving the combat plane clear.
        repeat(12) { sector ->
            val angle=sector*PI/6
            val radial=Vector(cos(angle),0.0,sin(angle))
            val tangent=Vector(-sin(angle),0.0,cos(angle))
            repeat(12) { i ->
                val t=i/12.0*2*PI
                val p=radial.clone().multiply((16.0+0.6*cos(phase))*scale)
                    .add(tangent.clone().multiply(sin(t)*1.4*scale)).setY((4.5+cos(t)*2.6)*scale)
                point(p,sector+tick/20,1.15f,i%3==0)
            }
        }
    }
    override fun sustain(tick: Int) {
        if(tick%5==0) motif(tick,1.0)
        if(tick%10==0) iris(-tick*0.006,9.5,13.4)
        SpectralScore.notes(tick).forEach { note ->
            sound(if(note.bass) Sound.BLOCK_NOTE_BLOCK_BELL else Sound.BLOCK_NOTE_BLOCK_CHIME,
                if(note.bass) 0.12f else 0.19f,note.pitch)
        }
        if(tick%80==0) sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE,0.22f,0.5f)
    }
    override fun beginDissolve() {
        sound(Sound.BLOCK_BEACON_DEACTIVATE,0.6f,0.6f)
        sound(Sound.BLOCK_GLASS_BREAK,0.65f,0.65f)
    }
    override fun dissolve(tick: Int) {
        if(tick%4!=0) return
        val remaining=(1-tick/40.0).coerceIn(0.0,1.0)
        motif(320+tick*3,remaining)
        // The optical cage breaks into twelve symmetric falling rays, then folds into its center.
        repeat(12) { i ->
            val angle=i*PI/6+tick*0.018
            repeat(4) { j ->
                val radius=(15.0-j*0.22)*remaining
                point(Vector(cos(angle)*radius,(12.0-tick*0.24+j*0.2).coerceAtLeast(0.3),sin(angle)*radius),i,0.85f,j==0)
            }
        }
        if(tick==40) sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.5f,0.55f)
    }
}
