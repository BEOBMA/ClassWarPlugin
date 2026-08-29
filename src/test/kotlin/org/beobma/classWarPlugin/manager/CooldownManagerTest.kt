package org.beobma.classWarPlugin.manager

import kotlin.test.Test
import kotlin.test.assertEquals

class CooldownManagerTest {
    @Test
    fun `two times flow halves the real cooldown`() {
        assertEquals(100L, CooldownManager.effectiveCooldownTicks(200, 2.0))
    }

    @Test
    fun `fractional flow rounds cooldown up to a whole tick`() {
        assertEquals(134L, CooldownManager.effectiveCooldownTicks(200, 1.5))
    }

    @Test
    fun `positive cooldown never disappears through a high multiplier`() {
        assertEquals(1L, CooldownManager.effectiveCooldownTicks(1, 10.0))
    }
}
