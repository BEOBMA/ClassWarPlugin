package org.beobma.classWarPlugin.damage

import kotlin.test.*

class ShieldDamageTest {
    @Test fun `ordinary shield damage keeps existing integer behavior`() {
        assertEquals(ShieldDamage.Result(0.0, 7), ShieldDamage.calculate(3.0, 10))
        assertEquals(ShieldDamage.Result(2.0, 0), ShieldDamage.calculate(3.0, 1))
    }
    @Test fun `hammer doubles shield consumption but not health damage`() {
        assertEquals(ShieldDamage.Result(0.0, 4), ShieldDamage.calculate(3.0, 10, 2.0))
        assertEquals(ShieldDamage.Result(1.0, 0), ShieldDamage.calculate(3.0, 4, 2.0))
        assertEquals(ShieldDamage.Result(2.5, 0), ShieldDamage.calculate(3.0, 1, 2.0))
        assertEquals(ShieldDamage.Result(3.0, 0), ShieldDamage.calculate(3.0, 0, 2.0))
    }
    @Test fun `invalid multipliers fall back safely`() {
        for (factor in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(ShieldDamage.calculate(3.0, 10), ShieldDamage.calculate(3.0, 10, factor))
        }
    }
}
