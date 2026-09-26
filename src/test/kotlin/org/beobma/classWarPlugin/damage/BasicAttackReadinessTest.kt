package org.beobma.classWarPlugin.damage

import kotlin.test.*

class BasicAttackReadinessTest {
    @Test fun `partial melee charge cannot trigger damage or on hit effects`() {
        for(charge in listOf(0f,0.5f,0.99f,0.9999f,Float.NaN)) assertFalse(BasicAttackReadiness.ready(charge))
        assertTrue(BasicAttackReadiness.ready(1f))
    }
}
