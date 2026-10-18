package org.beobma.classWarPlugin.damage

import kotlin.test.*

class BasicAttackReadinessTest {
    @Test fun `partial melee charge cannot trigger on hit effects`() {
        for(charge in listOf(0f,0.5f,0.99f,0.9999f,Float.NaN)) assertFalse(BasicAttackReadiness.ready(charge))
        assertTrue(BasicAttackReadiness.ready(1f))
    }

    @Test fun `pre attack charge survives reset and is shared by same tick sweeping hits only`() {
        val id = java.util.UUID.randomUUID()
        BasicAttackReadiness.record(id, 100, 0.25f)
        assertFalse(BasicAttackReadiness.effectsAllowed(id, 100))
        assertFalse(BasicAttackReadiness.effectsAllowed(id, 100))
        assertTrue(BasicAttackReadiness.effectsAllowed(id, 101))
        BasicAttackReadiness.record(id, 101, 1f)
        assertTrue(BasicAttackReadiness.effectsAllowed(id, 101))
        BasicAttackReadiness.clear(id)
    }

    @Test fun `a new same tick attack replaces the previous readiness and players are isolated`() {
        val a = java.util.UUID.randomUUID(); val b = java.util.UUID.randomUUID()
        BasicAttackReadiness.record(a, 5, 1f)
        BasicAttackReadiness.record(b, 5, 0.1f)
        assertTrue(BasicAttackReadiness.effectsAllowed(a, 5))
        assertFalse(BasicAttackReadiness.effectsAllowed(b, 5))
        BasicAttackReadiness.record(a, 5, 0f)
        assertFalse(BasicAttackReadiness.effectsAllowed(a, 5))
        BasicAttackReadiness.clear(a); BasicAttackReadiness.clear(b)
        assertTrue(BasicAttackReadiness.effectsAllowed(b, 5))
    }

    @Test fun `redirected and replayed attacks preserve proc eligibility without leaking to other paths`() {
        val id = java.util.UUID.randomUUID()
        AttackEffectContext.with(id, false) {
            assertFalse(AttackEffectContext.allowed(id, DamagePath.BASIC_ATTACK))
            assertTrue(AttackEffectContext.allowed(id, DamagePath.SKILL))
            assertTrue(AttackEffectContext.allowed(id, DamagePath.RANGED_ATTACK))
            assertTrue(AttackEffectContext.allowed(java.util.UUID.randomUUID(), DamagePath.BASIC_ATTACK))
            assertFailsWith<IllegalStateException> {
                AttackEffectContext.with(id, true) {
                    assertTrue(AttackEffectContext.allowed(id, DamagePath.BASIC_ATTACK))
                    error("test")
                }
            }
            assertFalse(AttackEffectContext.allowed(id, DamagePath.BASIC_ATTACK))
        }
        assertTrue(AttackEffectContext.allowed(id, DamagePath.BASIC_ATTACK))
    }
}
