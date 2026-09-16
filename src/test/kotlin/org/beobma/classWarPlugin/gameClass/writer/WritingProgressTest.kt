package org.beobma.classWarPlugin.gameClass.writer

import org.beobma.classWarPlugin.status.list.WritingStatus
import kotlin.test.*

class WritingProgressTest {
    @Test fun `correct and incorrect answers accumulate exact bonuses`() {
        val progress = WritingProgress()
        repeat(10) { assertTrue(progress.submit("동해 물과", "동해 물과")) }
        assertEquals(1.0, progress.basicDamageBonus)
        assertEquals(0.9, progress.damageTakenMultiplier, 1e-10)
        assertFalse(progress.submit("동해 물과", "동해물과"))
        assertEquals(0.8, progress.basicDamageBonus)
        assertEquals(0.92, progress.damageTakenMultiplier, 1e-10)
        assertEquals(10, progress.correct)
        assertEquals(1, progress.incorrect)
    }

    @Test fun `spaces and punctuation are not silently corrected`() {
        val progress = WritingProgress()
        assertFalse(progress.submit("한 줄.", "한 줄"))
        assertFalse(progress.submit("한 줄.", " 한 줄."))
        assertFalse(progress.submit("한 줄.", "한 줄. "))
        assertEquals(-0.6, progress.basicDamageBonus)
        assertEquals(1.06, progress.damageTakenMultiplier, 1e-10)
    }

    @Test fun `damage reduction cannot become negative healing`() {
        val progress = WritingProgress()
        repeat(150) { progress.submit("글", "글") }
        assertEquals(0.0, progress.damageTakenMultiplier)
        repeat(30) { progress.submit("글", "오답") }
        assertEquals(0.1, progress.damageTakenMultiplier, 1e-10)
    }

    @Test fun `library contains many keyboard-ready works and lines`() {
        assertTrue(WritingLibrary.works.size >= 60)
        assertTrue(WritingLibrary.works.sumOf { it.lines.size } >= 240)
        assertTrue(WritingLibrary.works.any { it.title == "애국가" })
        assertTrue(WritingLibrary.works.any { it.title == "이상 · 날개 중에서" })
        assertTrue(WritingLibrary.works.any { it.title == "이상 · 거울 중에서" })
        assertTrue(WritingLibrary.works.count { it.title.startsWith("이상 · 오감도") } >= 6)
        assertEquals(WritingLibrary.works.size, WritingLibrary.works.map { it.title }.distinct().size)
        assertTrue(WritingLibrary.works.all { it.lines.isNotEmpty() })
        WritingLibrary.works.flatMap { it.lines }.forEach { line ->
            assertTrue(line.isNotBlank())
            assertEquals(line.trim(), line)
            assertTrue(line.length <= 256, "Chat input is too long: $line")
            assertFalse(line.any { it.isISOControl() || it == '\u200B' || it == '\uFEFF' })
        }
    }

    @Test fun `writing uses persistent keyword action bar progress`() {
        val status = WritingStatus()
        status.synchronize(12, 2, 3, 16)
        assertTrue(status.isClassMechanic)
        assertFalse(status.canRemove)
        assertNull(status.duration)
        assertTrue(status.actionBarText().contains("3/16"))
        assertTrue(status.actionBarText().contains("정답 12"))
        assertTrue(status.actionBarText().contains("오답 2"))
    }

    @Test fun `typing difference marks replacements insertions and omissions`() {
        val replacement = TypingDifference.compare("대한 사람", "대한 사림")
        assertContentEquals(booleanArrayOf(true, true, true, true, false), replacement.expectedCorrect)
        assertContentEquals(booleanArrayOf(true, true, true, true, false), replacement.inputCorrect)

        val insertion = TypingDifference.compare("동해물", "동해 물")
        assertContentEquals(booleanArrayOf(true, true, true), insertion.expectedCorrect)
        assertContentEquals(booleanArrayOf(true, true, false, true), insertion.inputCorrect)

        val omission = TypingDifference.compare("동해 물", "동해물")
        assertContentEquals(booleanArrayOf(true, true, false, true), omission.expectedCorrect)
        assertContentEquals(booleanArrayOf(true, true, true), omission.inputCorrect)
    }
}
