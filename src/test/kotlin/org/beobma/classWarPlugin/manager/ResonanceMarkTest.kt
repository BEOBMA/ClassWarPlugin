package org.beobma.classWarPlugin.manager

import kotlin.test.Test
import kotlin.test.assertEquals

class ResonanceMarkTest {
    @Test fun `thirty aftermath completes only one third of the ring`() {
        assertEquals(10, ResonanceMarkManager.filledSteps(0, 10))
        assertEquals(29, ResonanceMarkManager.filledSteps(0, 29))
        assertEquals(30, ResonanceMarkManager.filledSteps(1, 0))
        assertEquals(45, ResonanceMarkManager.filledSteps(1, 15))
        assertEquals(60, ResonanceMarkManager.filledSteps(2, 0))
        assertEquals(90, ResonanceMarkManager.filledSteps(3, 0))
    }

    @Test fun `consuming resonance retains partial aftermath and removes one sector`() {
        assertEquals(30, ResonanceMarkManager.filledSteps(2, 17) - ResonanceMarkManager.filledSteps(1, 17))
        assertEquals(90, ResonanceMarkManager.filledSteps(3, 29))
        assertEquals(0, ResonanceMarkManager.filledSteps(0, 0))
    }
}
