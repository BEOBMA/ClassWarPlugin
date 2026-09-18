package org.beobma.classWarPlugin.game

import java.util.UUID
import kotlin.test.*

class ClassSelectionHistoryTest {
    @Test fun `all offers including both dual slots stay excluded only for their player`() {
        val history = ClassSelectionHistory()
        val player = UUID.randomUUID()
        history.record(player, listOf("a", "b"))
        history.record(player, listOf("c", "d"))
        assertEquals(setOf("a", "b", "c", "d"), history.exclusions(player, listOf("c", "d"), true))
        assertEquals(setOf("e"), history.exclusions(UUID.randomUUID(), listOf("e"), true))
        assertEquals(setOf("c", "d"), history.exclusions(player, listOf("c", "d"), false))
        history.clear()
        assertEquals(emptySet(), history.exclusions(player, emptyList(), true))
    }

    @Test fun `exhausted pool stays empty without resetting history`() {
        val history = ClassSelectionHistory()
        val player = UUID.randomUUID()
        val pool = listOf("a", "b", "c")
        history.record(player, pool)
        repeat(2) {
            assertTrue(pool.filterNot { it in history.exclusions(player, listOf("c"), true) }.isEmpty())
        }
        assertEquals(listOf("a", "b"), pool.filterNot { it in history.exclusions(player, listOf("c"), false) })
    }

    @Test fun `every mode enables repeat exclusion by default and settings can disable it`() {
        val settings = GameConfiguration(startingItems = emptyList())
        assertTrue(settings.excludePreviousClasses)
        assertFalse(settings.copy(excludePreviousClasses = false).excludePreviousClasses)
    }
}
