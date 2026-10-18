package org.beobma.classWarPlugin.gameClass.kaleidoscope

import kotlin.math.pow

/** Quiet original chime ostinato: opening, lift, suspension, then resolution across sixteen seconds. */
internal object SpectralScore {
    data class Note(val pitch: Float, val bass: Boolean)
    private val chords=listOf(listOf(0,7,12),listOf(3,7,14),listOf(5,12,17),listOf(7,14,19),
        listOf(8,15,20),listOf(7,12,19),listOf(3,10,15),listOf(0,7,12))
    fun notes(tick: Int): List<Note> {
        if(tick !in 0 until 320 || tick%10!=0) return emptyList()
        val chord=chords[tick/40]
        val step=(tick%40)/10
        val semitone=chord[listOf(0,1,2,1)[step]]
        val melody=Note((0.5*2.0.pow(semitone/12.0)).toFloat(),false)
        return if(step==0) listOf(melody,Note((0.5*2.0.pow(chord[0]/12.0)).toFloat(),true)) else listOf(melody)
    }
}
