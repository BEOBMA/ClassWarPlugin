package org.beobma.classWarPlugin.manager

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassBalanceModifiersTest {
    @Test
    fun `defaults preserve current class values`() {
        val modifiers = ClassBalanceModifiers()

        ClassBalanceField.entries.forEach { field ->
            assertEquals(1.0, modifiers.value(field))
        }
    }

    @Test
    fun `overall multiplier composes with combat value multipliers`() {
        val modifiers = ClassBalanceModifiers(overallMultiplier = 1.5, damageMultiplier = 1.2)

        assertEquals(1.8, modifiers.effective(ClassBalanceField.DAMAGE), absoluteTolerance = 0.000001)
    }

    @Test
    fun `cooldown flow stays independent from overall value multiplier`() {
        val modifiers = ClassBalanceModifiers(overallMultiplier = 2.0, cooldownFlowMultiplier = 1.3)

        assertEquals(1.3, modifiers.effective(ClassBalanceField.COOLDOWN_FLOW))
    }
}
