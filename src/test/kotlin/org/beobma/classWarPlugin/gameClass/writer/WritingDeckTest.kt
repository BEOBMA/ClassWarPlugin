package org.beobma.classWarPlugin.gameClass.writer

import kotlin.random.Random
import kotlin.test.*

class WritingDeckTest {
    @Test fun `first work is not fixed to the anthem`() {
        val firstTitles = (0..100).map { WritingDeck(random = Random(it)).next().title }.toSet()
        assertTrue(firstTitles.size > 1)
        assertTrue(firstTitles.any { it != "애국가" })
        assertTrue("애국가" in firstTitles)
    }

    @Test fun `every work appears once per cycle with original line order`() {
        val deck = WritingDeck(random = Random(12))
        val cycle = List(WritingLibrary.works.size) { deck.next() }
        assertEquals(WritingLibrary.works.toSet(), cycle.toSet())
        cycle.forEach { work -> assertEquals(WritingLibrary.works.first { it.title == work.title }.lines, work.lines) }
        assertNotEquals(cycle.last(), deck.next())
    }

    @Test fun `reset starts a fresh complete shuffled cycle`() {
        val deck = WritingDeck(random = Random(7))
        repeat(3) { deck.next() }
        deck.reset()
        assertEquals(WritingLibrary.works.toSet(), List(WritingLibrary.works.size) { deck.next() }.toSet())
    }
}
