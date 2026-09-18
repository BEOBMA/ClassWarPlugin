package org.beobma.classWarPlugin.gameClass.metronome

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.growth.GrowthClassCatalog
import org.beobma.classWarPlugin.growth.GrowthCombatStyle
import kotlin.math.roundToLong
import kotlin.test.*

class MetronomeTest {
    @Test fun `preview plays all 24 complete songs twice and all click ticks accept attacks`() {
        val transport = RhythmTransport()
        val state = RhythmState()
        var absoluteTick = 0L
        repeat(2) {
            MetronomeScore.scores.forEachIndexed { index, score ->
                val duration = (32 * 1200.0 / score.bpm).roundToLong().toInt()
                repeat(duration) { localTick ->
                    val frame = transport.previewTick()
                    assertEquals(index + 1, transport.stage)
                    assertEquals(score.bpm, frame.timing.bpm)
                    assertEquals(localTick.toLong(), frame.timing.elapsedTicks)
                    if (frame.beat != null) {
                        assertTrue(state.attack(absoluteTick, frame.timing), "click at ${score.title} tick $localTick")
                        assertEquals(0, frame.timing.phase, "beat indicator at ${score.title} tick $localTick")
                    }
                    absoluteTick++
                }
            }
        }
    }

    @Test fun `preview selection produces note and accented click together at tick zero`() {
        val transport = RhythmTransport()
        MetronomeScore.scores.forEachIndexed { index, score ->
            transport.selectPreviewStage(index + 1)
            val frame = transport.previewTick()
            assertEquals(score.notesByPulse[0], frame.notes)
            assertEquals(0, frame.beat)
            assertEquals(0, frame.timing.phase)
            assertTrue(frame.timing.isOnGrid)
        }
        transport.reset()
        val waiting = transport.tick(0)
        assertTrue(waiting.notes.isEmpty())
        assertEquals(60, waiting.timing.bpm)
        assertEquals(0, waiting.beat)
    }

    @Test fun `reference subdivisions remain playable and density controls progression`() {
        for (division in listOf(1, 2, 4, 8)) {
            val rhythm = RhythmState()
            repeat(240) { index -> assertTrue(rhythm.attack((index * 20.0 / division).roundToLong())) }
            assertEquals(240, rhythm.streak)
            assertEquals(division, rhythm.subdivision)
            assertEquals(when (division) { 1 -> 4; 2 -> 10; 4 -> 16; else -> 24 }, rhythm.stage)
        }
    }

    @Test fun `musical subdivisions use their actual BPM including non-integer tick periods`() {
        MetronomeScore.tempos.forEach { bpm ->
            val divisions = if (bpm <= 72) listOf(1, 2, 4, 8) else listOf(1, 2, 4)
            divisions.forEach { division ->
                val rhythm = RhythmState()
                repeat(400) { index ->
                    val tick = (index * 1200.0 / (bpm * division)).roundToLong()
                    assertTrue(rhythm.attack(tick, RhythmTiming(7, tick, bpm)), "$bpm / $division at $tick")
                }
            }
        }
    }

    @Test fun `mistiming and repeated packets reset streak without reopening consumed slot`() {
        val rhythm = RhythmState()
        repeat(20) { assertTrue(rhythm.attack(it * 5L)) }
        assertFalse(rhythm.attack(96))
        assertEquals(0, rhythm.streak)
        assertEquals(1.0, rhythm.damageMultiplier)
        assertTrue(rhythm.attack(100))
        assertFalse(rhythm.attack(100))
        assertFalse(rhythm.attack(100))
        assertEquals(0, rhythm.streak)
    }

    @Test fun `tempo transition permits new downbeat without comparing slots from previous song`() {
        val rhythm = RhythmState()
        repeat(20) { rhythm.attack(it * 5L) }
        assertTrue(rhythm.attack(100, RhythmTiming(1, 0, 88)))
        assertEquals(21, rhythm.streak)
        assertFalse(rhythm.attack(100, RhythmTiming(1, 0, 88)))
    }

    @Test fun `spamming never reaches advanced stages and inactivity resets damage`() {
        val spam = RhythmState()
        repeat(200) { spam.attack(it.toLong()); assertEquals(0, spam.stage) }
        val rhythm = RhythmState()
        repeat(10) { rhythm.attack(it * 20L) }
        assertFalse(rhythm.expire(220))
        assertTrue(rhythm.expire(221))
        assertEquals(0, rhythm.streak)
        assertEquals(1.0, rhythm.damageMultiplier)
    }

    @Test fun `faster accurate attacks earn more damage without exceeding cap`() {
        val slow = RhythmState()
        val fast = RhythmState()
        repeat(9) { slow.attack(it * 20L) }
        repeat(65) { fast.attack((it * 2.5).roundToLong()) }
        assertTrue(fast.damageMultiplier > slow.damageMultiplier)
        repeat(1000) { fast.attack(((it + 65) * 2.5).roundToLong()) }
        assertEquals(2.5, fast.damageMultiplier)
    }

    @Test fun `24 compositions have unique melodies with harmony and bounded note block voices`() {
        assertEquals(24, MetronomeScore.count)
        assertEquals(12, MetronomeScore.scores.map { it.bpm }.distinct().size)
        assertEquals(24, MetronomeScore.scores.map { it.title }.distinct().size)
        val melodies = mutableSetOf<List<Pair<Int, Int>>>()
        MetronomeScore.scores.forEachIndexed { index, score ->
            assertTrue(score.notesByPulse.keys.all { it in 0 until RhythmScore.PULSES_PER_LOOP })
            val notes = score.notesByPulse.values.flatten()
            assertTrue(notes.all { it.key in 0..24 && it.pitch in 0.5f..2.0f && it.volume in 0f..1f })
            assertTrue(notes.map { it.key }.distinct().size >= 8)
            assertTrue(notes.any { it.instrument == RhythmInstrument.BASS })
            assertTrue(notes.any { it.instrument == RhythmInstrument.GUITAR })
            assertTrue(score.notesByPulse.values.maxOf { it.size } in 4..12)
            val lead = listOf(RhythmInstrument.HARP, RhythmInstrument.FLUTE, RhythmInstrument.PLING, RhythmInstrument.BIT)[index / 6]
            melodies += score.notesByPulse.toSortedMap().flatMap { (pulse, events) ->
                events.filter { it.instrument == lead && it.volume >= 0.4f }.map { pulse to it.key }
            }
        }
        assertEquals(24, melodies.size)
    }

    @Test fun `fractional tempos never accumulate rounding drift across long musical loops`() {
        MetronomeScore.scores.forEachIndexed { index, score ->
            val transport = RhythmTransport()
            transport.selectPreviewStage(index + 1)
            val ticks = (32 * 12 * 1200.0 / score.bpm).roundToLong()
            val expected = mutableMapOf<Long, MutableList<RhythmNote>>()
            repeat(12) { cycle ->
                score.notesByPulse.forEach { (pulse, events) ->
                    val tick = ((cycle * 256L + pulse) * 1200.0 / (score.bpm * 8)).roundToLong()
                    expected.getOrPut(tick) { mutableListOf() }.addAll(events)
                }
            }
            repeat(ticks.toInt()) { tick ->
                val frame = transport.tick(index + 1)
                assertEquals(expected[tick.toLong()].orEmpty(), frame.notes, "${score.title} at $tick")
                if (frame.beat != null) assertTrue(frame.timing.isOnGrid)
            }
        }
    }

    @Test fun `stage changes occur on accented downbeats and never skip a song`() {
        val transport = RhythmTransport()
        var previousStage = 0
        repeat(6000) {
            val frame = transport.tick(24)
            if (transport.stage != previousStage) {
                assertEquals(previousStage + 1, transport.stage)
                assertEquals(0, frame.beat)
                assertEquals(0L, frame.timing.elapsedTicks)
                assertEquals(transport.score!!.notesByPulse[0], frame.notes)
                assertTrue(frame.timing.isOnGrid)
                previousStage = transport.stage
            }
        }
        assertEquals(24, previousStage)
        assertTrue("metronome" in AbilityCatalog.enabledClassIds())
        assertEquals(GrowthCombatStyle.BASIC, GrowthClassCatalog.style("metronome"))
    }
}
