package org.beobma.classWarPlugin.gameClass.warCorrespondent

import kotlin.test.*
import org.beobma.classWarPlugin.gameClass.warCorrespondent.RecordingProgress.Result

class RecordingProgressTest {
    @Test fun `enhanced broadcast lasts eighty ticks regardless of ordinary completion conditions`() {
        val progress = RecordingProgress<String, String>(enhanced = true)
        repeat(79) { assertEquals(Result.RECORDING, progress.advance(setOf("first", "second"), setOf("grave"))) }
        assertEquals(Result.COMPLETED, progress.advance(emptySet(), emptySet()))
        assertEquals(80, progress.elapsed)
        repeat(5) { assertEquals(Result.COMPLETED, progress.advance(setOf("late"), emptySet())) }
        assertEquals(80, progress.elapsed)
    }

    @Test fun `enhanced broadcast can complete without any subjects`() {
        val progress = RecordingProgress<String, String>(enhanced = true)
        repeat(80) { progress.advance(emptySet(), emptySet()) }
        assertEquals(Result.COMPLETED, progress.result)
    }

    @Test fun `two subjects must each reach one second of continuous exposure`() {
        val progress = RecordingProgress<String, String>()
        repeat(10) { assertEquals(Result.RECORDING, progress.advance(setOf("first"), emptySet())) }
        repeat(9) { assertEquals(Result.RECORDING, progress.advance(setOf("first", "second"), emptySet())) }
        assertEquals(0.95, progress.playerProgress("first"))
        assertEquals(0.45, progress.playerProgress("second"))
        repeat(10) { assertEquals(Result.RECORDING, progress.advance(setOf("first", "second"), emptySet())) }
        assertEquals(Result.COMPLETED, progress.advance(setOf("first", "second"), emptySet()))
        assertEquals(1.0, progress.playerProgress("second"))
        assertEquals(Result.COMPLETED, progress.advance(emptySet(), emptySet()))
        assertEquals(30, progress.elapsed)
    }

    @Test fun `leaving and reentering clears only that subjects exposure`() {
        val progress = RecordingProgress<String, String>()
        repeat(15) { progress.advance(setOf("first", "second"), setOf("grave")) }
        progress.advance(setOf("first"), emptySet())
        assertEquals(0.8, progress.playerProgress("first"))
        assertEquals(0.0, progress.playerProgress("second"))
        assertEquals(0.0, progress.deathProgress("grave"))
        progress.advance(setOf("first", "second"), setOf("grave"))
        assertEquals(0.05, progress.playerProgress("second"))
        assertEquals(0.025, progress.deathProgress("grave"))
    }

    @Test fun `one death location needs two seconds and cannot combine separate locations`() {
        val progress = RecordingProgress<String, String>()
        repeat(20) { progress.advance(emptySet(), setOf("first grave")) }
        repeat(39) { assertEquals(Result.RECORDING, progress.advance(emptySet(), setOf("second grave"))) }
        assertEquals(Result.COMPLETED, progress.advance(emptySet(), setOf("second grave")))
    }

    @Test fun `an incomplete recording expires after four seconds while last tick completion succeeds`() {
        val empty = RecordingProgress<String, String>()
        repeat(79) { assertEquals(Result.RECORDING, empty.advance(emptySet(), emptySet())) }
        assertEquals(Result.EXPIRED, empty.advance(emptySet(), emptySet()))
        assertEquals(Result.EXPIRED, empty.advance(setOf("late"), emptySet()))
        val late = RecordingProgress<String, String>()
        repeat(40) { late.advance(emptySet(), emptySet()) }
        repeat(39) { late.advance(emptySet(), setOf("grave")) }
        assertEquals(Result.COMPLETED, late.advance(emptySet(), setOf("grave")))
    }
}
