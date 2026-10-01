package org.beobma.classWarPlugin.manager

import kotlin.test.*

class IndicatorLayoutTest {
    @Test fun `numeric hits and labels have disjoint lanes`() {
        val lanes = (0..2).map { IndicatorLayout.slots(it).toSet() }
        assertTrue(lanes[0].intersect(lanes[1]).isEmpty())
        assertTrue(lanes[1].intersect(lanes[2]).isEmpty())
        assertEquals(8, lanes.flatten().toSet().size)
    }
    @Test fun `global cap preserves higher priority text`() {
        assertNull(IndicatorLayout.eviction(listOf(2, 1, 2), 0))
        assertEquals(1, IndicatorLayout.eviction(listOf(2, 0, 1), 2))
        assertEquals(1, IndicatorLayout.eviction(listOf(2, 1, 2), 1))
        assertEquals(0, IndicatorLayout.eviction(listOf(2, 2), 2))
    }
}
