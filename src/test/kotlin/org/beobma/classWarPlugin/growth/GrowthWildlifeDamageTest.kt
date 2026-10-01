package org.beobma.classWarPlugin.growth

import kotlin.test.*

class GrowthWildlifeDamageTest {
    @Test fun `wildlife attack stays between two and three at any level`() {
        for (level in listOf(-1, 0, 1, 20, 50, 100, Int.MAX_VALUE)) {
            assertTrue(GrowthWildlifeDamage.baseDamage(level) in 2.0..3.0)
        }
        assertEquals(3.0, GrowthWildlifeDamage.baseDamage(100))
    }
    @Test fun `cap preserves smaller hits and limits amplified or ranged hits`() {
        for (damage in listOf(0.0, 0.25, 1.0, 2.0, 3.0)) assertEquals(damage, GrowthWildlifeDamage.limit(damage))
        for (damage in listOf(4.0, 20.0, 1000.0)) assertEquals(3.0, GrowthWildlifeDamage.limit(damage))
    }
}
