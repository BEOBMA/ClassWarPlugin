package org.beobma.classWarPlugin.gameClass.metronome

import kotlin.math.pow
import kotlin.math.roundToLong

internal enum class RhythmInstrument {
    HARP, GUITAR, FLUTE, BASS, BELL, PLING, BIT, KICK, SNARE, HAT,
}

internal data class RhythmNote(val instrument: RhythmInstrument, val key: Int, val volume: Float) {
    val pitch: Float get() = 2.0.pow((key - 12) / 12.0).toFloat()
}

/** Score positions are musical eighths of a quarter note, never rounded bar durations. */
internal data class RhythmScore(val title: String, val bpm: Int, val notesByPulse: Map<Int, List<RhythmNote>>) {
    companion object {
        const val PULSES_PER_BEAT = 8
        const val PULSES_PER_BAR = 32
        const val PULSES_PER_LOOP = 256 // eight bars
    }
}

/**
 * Original 24-piece suite "진자의 스물네 장면". Each theme has its own written question/answer
 * melody, chord progression and groove. No external songs, recordings or MIDI are used.
 * Notes stay in the native note-block 0..24 range.
 */
internal object MetronomeScore {
    val tempos = listOf(60, 66, 72, 80, 88, 96, 108, 120, 132, 144, 160, 180)
    private data class Theme(
        val title: String, val tonic: Int, val major: Boolean,
        val melody: List<Int>, val harmony: List<Int>, val groove: Int,
    )
    private fun theme(title: String, tonic: Int, major: Boolean, melody: String, harmony: String, groove: Int) =
        Theme(title, tonic, major, melody.split(' ').map(String::toInt), harmony.split(' ').map(String::toInt), groove)

    private val themes = listOf(
        theme("첫 진자", 3, false, "0 1 2 4 3 2 1 0 4 3 2 1 0 2 1 0", "0 5 2 6 3 0 5 4", 0),
        theme("새벽의 물방울", 6, true, "4 2 1 0 1 2 4 5 6 5 4 2 3 1 2 0", "0 3 5 4 1 3 4 0", 1),
        theme("안개 속 우편", 8, false, "0 2 1 3 2 4 3 1 5 4 2 0 1 3 2 0", "0 3 6 2 5 1 4 0", 2),
        theme("작은 등대", 1, true, "0 4 2 5 4 2 1 2 3 5 6 4 2 1 4 0", "0 4 5 3 0 1 4 0", 3),
        theme("유리 계단", 3, false, "0 1 2 3 4 2 3 5 6 4 3 1 2 4 1 0", "0 5 3 4 0 2 5 4", 1),
        theme("햇살의 태엽", 10, true, "2 4 5 4 2 1 0 2 4 6 5 3 2 4 1 0", "0 5 1 4 3 0 4 0", 0),
        theme("은빛 나침반", 8, false, "4 0 2 1 3 2 4 6 5 3 4 2 1 3 1 0", "0 6 5 4 3 5 1 4", 2),
        theme("종이비행의 궤적", 6, true, "0 2 4 6 5 4 2 1 2 5 4 3 1 2 4 0", "0 2 3 4 5 1 4 0", 3),
        theme("푸른 역의 왈츠빛", 1, false, "0 3 2 0 4 3 1 2 5 4 6 3 2 1 4 0", "0 3 5 6 2 5 4 0", 0),
        theme("나무 사이의 바람", 8, true, "4 5 2 3 1 2 0 4 6 5 3 4 2 1 2 0", "0 3 0 4 5 3 1 4", 1),
        theme("맥박의 행진", 3, false, "0 0 4 2 3 1 4 5 6 4 2 3 1 4 2 0", "0 5 6 4 3 0 5 4", 3),
        theme("구리빛 기관", 10, false, "2 0 3 1 4 2 5 3 6 4 5 2 3 1 4 0", "0 4 0 6 5 3 1 4", 2),
        theme("별자리 추적", 6, false, "0 4 1 5 2 6 3 4 5 2 4 1 3 0 1 0", "0 2 6 3 5 1 4 0", 1),
        theme("불꽃의 초대", 1, true, "2 4 0 2 5 4 6 5 4 1 3 2 5 4 1 0", "0 4 1 5 3 2 4 0", 3),
        theme("수평선 너머", 8, true, "0 2 5 4 6 5 3 4 2 4 6 5 3 2 1 0", "0 5 3 1 4 2 5 4", 0),
        theme("폭풍 전의 약속", 3, false, "4 3 1 0 2 4 5 6 5 4 2 1 3 2 4 0", "0 6 3 5 2 1 4 0", 2),
        theme("붉은 궤도", 10, false, "0 1 4 2 5 3 6 4 5 6 4 2 3 1 2 0", "0 5 1 4 6 3 5 4", 3),
        theme("천 개의 등불", 6, true, "4 2 5 3 6 4 2 0 1 3 5 6 4 2 1 0", "0 3 2 5 1 4 3 0", 1),
        theme("심장의 도약", 8, false, "0 4 0 5 1 6 2 4 3 5 2 4 1 3 4 0", "0 3 6 5 2 0 1 4", 2),
        theme("빛을 향한 질주", 1, true, "0 1 4 5 2 3 6 5 4 6 3 5 2 4 1 0", "0 5 4 3 1 2 4 0", 0),
        theme("새벽의 질주", 3, false, "4 2 3 5 4 6 5 3 2 4 1 3 0 2 4 0", "0 5 2 6 3 0 1 4", 3),
        theme("유성의 약속", 10, true, "2 5 4 6 3 5 2 4 1 4 3 5 2 1 4 0", "0 2 5 1 3 4 5 0", 1),
        theme("마지막 태엽", 6, false, "0 2 1 4 3 6 4 5 6 3 5 2 4 1 2 0", "0 6 5 3 2 1 5 4", 2),
        theme("일출의 피날레", 3, true, "0 2 4 5 6 4 5 6 5 3 4 2 3 1 4 0", "0 4 5 3 1 2 4 0", 0),
    )
    val scores = themes.mapIndexed { index, theme -> compose(theme, tempos[index / 2], index / 6 + 1) }
    val count: Int get() = scores.size

    private fun compose(theme: Theme, bpm: Int, intensity: Int): RhythmScore {
        val scale = if (theme.major) intArrayOf(0, 2, 4, 5, 7, 9, 11) else intArrayOf(0, 2, 3, 5, 7, 8, 10)
        fun key(degree: Int, octave: Int = 0): Int {
            var value = theme.tonic + scale[degree % 7] + (degree / 7 + octave) * 12
            while (value > 24) value -= 12
            return value
        }
        val events = mutableMapOf<Int, MutableList<RhythmNote>>()
        fun note(bar: Int, pulse: Int, instrument: RhythmInstrument, key: Int, volume: Float) {
            require(key in 0..24 && pulse in 0..31)
            events.getOrPut(bar * 32 + pulse) { mutableListOf() }.add(RhythmNote(instrument, key, volume))
        }
        val melodyRhythm = arrayOf(
            intArrayOf(0, 4, 8, 12, 16, 20, 24, 28),
            intArrayOf(0, 6, 8, 12, 16, 22, 24, 28),
            intArrayOf(0, 4, 10, 12, 16, 20, 26, 28),
            intArrayOf(0, 4, 8, 14, 16, 20, 24, 30),
        )[theme.groove]
        val lead = when (intensity) { 1 -> RhythmInstrument.HARP; 2 -> RhythmInstrument.FLUTE; 3 -> RhythmInstrument.PLING; else -> RhythmInstrument.BIT }
        repeat(8) { bar ->
            val root = theme.harmony[bar]
            val chord = intArrayOf(key(root), key(root + 2), key(root + 4), key(root + 6))
            repeat(8) melody@ { step ->
                if (intensity == 1 && step % 2 != 0 && bar % 4 != 3) return@melody
                val phrase = if (bar % 4 < 2) 0 else 8
                val degree = theme.melody[phrase + step] + if (bar in 4..6 && step % 3 == 0) 2 else 0
                note(bar, melodyRhythm[step], lead, key(degree, 1), if (step == 0) 0.55f else 0.4f)
            }
            for (pulse in listOf(0, 16)) {
                chord.take(3).forEach { note(bar, pulse, RhythmInstrument.GUITAR, it, 0.16f) }
                note(bar, pulse, RhythmInstrument.BASS, key(root) % 12, 0.46f)
            }
            if (intensity >= 2) {
                repeat(8) { step -> note(bar, step * 4 + 2, RhythmInstrument.HARP, chord[(step + theme.groove) % 4], 0.15f) }
                note(bar, 8, RhythmInstrument.BASS, key(root + 4) % 12, 0.3f)
                note(bar, 28, RhythmInstrument.BASS, key(theme.harmony[(bar + 1) % 8]) % 12, 0.28f)
            }
            if (intensity >= 3) {
                note(bar, 0, RhythmInstrument.KICK, 8, 0.38f)
                note(bar, 16, RhythmInstrument.KICK, 8, 0.3f)
                note(bar, 8, RhythmInstrument.SNARE, 12, 0.24f)
                note(bar, 24, RhythmInstrument.SNARE, 12, 0.3f)
                repeat(8) { note(bar, it * 4, RhythmInstrument.HAT, 16, if (it % 2 == 0) 0.10f else 0.06f) }
            }
            if (intensity == 4) {
                note(bar, 0, RhythmInstrument.BELL, key(theme.melody[0], 1), 0.17f)
                note(bar, 12 + (theme.groove % 2) * 2, RhythmInstrument.KICK, 10, 0.23f)
                repeat(4) { note(bar, it * 8 + 6, RhythmInstrument.FLUTE, chord[(it + 2) % 4], 0.16f) }
                if (bar == 7) repeat(4) { note(bar, 24 + it * 2, RhythmInstrument.SNARE, 12 + it, 0.13f + it * 0.025f) }
            }
        }
        return RhythmScore(theme.title, bpm, events.mapValues { it.value.toList() })
    }
}

/** All attacks and audible events use exactly the same rounded absolute musical positions. */
internal data class RhythmTiming(val epoch: Long, val elapsedTicks: Long, val bpm: Int) {
    val subdivisions: Int get() = if (bpm <= 72) 8 else 4
    val slot: Long get() = (elapsedTicks * bpm * subdivisions / 1200.0).roundToLong()
    val isOnGrid: Boolean get() = (slot * 1200.0 / (bpm * subdivisions)).roundToLong() == elapsedTicks
    /** Visual phase interpolates between the same rounded beat ticks that emit the click. */
    val beatPosition: Double get() {
        val period = 1200.0 / bpm
        var beat = (elapsedTicks / period).toLong()
        if (elapsedTicks >= ((beat + 1) * period).roundToLong()) beat++
        val start = (beat * period).roundToLong()
        val end = ((beat + 1) * period).roundToLong()
        return beat + (elapsedTicks - start).toDouble() / (end - start)
    }
    val phase: Int get() = ((beatPosition % 1.0) * 8).toInt().coerceIn(0, 7)
}

internal data class RhythmFrame(
    val timing: RhythmTiming, val notes: List<RhythmNote>, val beat: Int?,
)

/** A single transport drives notes, click, judgement and UI. No separately rounded bars or loops. */
internal class RhythmTransport {
    var stage = 0
        private set
    private var epoch = 0L
    private var elapsed = 0L
    private var nextPulse = 0L
    val score: RhythmScore? get() = MetronomeScore.scores.getOrNull(stage - 1)
    private val bpm: Int get() = score?.bpm ?: 60
    fun reset() { select(0) }
    private fun select(stage: Int) {
        this.stage = stage
        epoch++
        elapsed = 0
        nextPulse = 0
    }
    fun selectPreviewStage(stage: Int) {
        require(stage in 1..MetronomeScore.count)
        select(stage)
    }
    private fun pulseTick(pulse: Long): Long = (pulse * 1200.0 / (bpm * RhythmScore.PULSES_PER_BEAT)).roundToLong()
    fun previewTick(): RhythmFrame = advance(stage.coerceAtLeast(1), true)
    fun tick(requestedStage: Int): RhythmFrame = advance(requestedStage.coerceIn(0, MetronomeScore.count), false)

    private fun advance(requested: Int, preview: Boolean): RhythmFrame {
        if (preview && stage == 0) select(1)
        if (!preview && requested == 0 && stage != 0) select(0)
        val notes = mutableListOf<RhythmNote>()
        var beat: Int? = null
        var switched = false
        while (pulseTick(nextPulse) <= elapsed) {
            // A boundary can share a server tick with the preceding pulse at fractional tempos.
            // Check each due pulse, not just the first one, and switch at most once per frame.
            if (!switched && preview && nextPulse > 0 && nextPulse % RhythmScore.PULSES_PER_LOOP == 0L) {
                select(stage % MetronomeScore.count + 1)
                switched = true
            } else if (!switched && !preview && requested > 0 && requested != stage &&
                nextPulse % (if (stage == 0) 8 else RhythmScore.PULSES_PER_BAR) == 0L) {
                select(if (requested > stage) stage + 1 else requested)
                switched = true
            }
            val pulse = (nextPulse % RhythmScore.PULSES_PER_LOOP).toInt()
            notes.addAll(score?.notesByPulse?.get(pulse).orEmpty())
            if (nextPulse % RhythmScore.PULSES_PER_BEAT == 0L) beat = (nextPulse / 8 % 4).toInt()
            nextPulse++
        }
        return RhythmFrame(RhythmTiming(epoch, elapsed++, bpm), notes, beat)
    }
}
