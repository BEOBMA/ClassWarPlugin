package org.beobma.classWarPlugin.gameClass.pioneer

import java.util.UUID
import kotlin.test.*

class PioneerStateTest {
    @Test fun `foresight check is read only and successful use spends exactly five`() {
        val state = PioneerState()
        repeat(3) { assertTrue(state.canSpendForesight(5)) }
        assertEquals(30, state.foresight)
        assertTrue(state.spendForesight(5))
        assertEquals(25, state.foresight)
        repeat(5) { assertTrue(state.spendForesight(5)) }
        assertEquals(0, state.foresight)
        assertFalse(state.spendForesight(5))
        assertEquals(0, state.foresight)
    }
    @Test fun `insufficient foresight is neither usable nor partially consumed`() {
        val state = PioneerState()
        for (amount in 0..4) {
            state.foresight = amount
            assertFalse(state.canSpendForesight(5))
            assertFalse(state.spendForesight(5))
            assertEquals(amount, state.foresight)
        }
        state.foresight = 5
        assertTrue(state.spendForesight(5))
        assertEquals(0, state.foresight)
    }
    @Test fun `resource changes between authorization and use are rechecked`() {
        val state = PioneerState()
        state.foresight = 5
        assertTrue(state.canSpendForesight(5))
        state.foresight -= 3
        assertFalse(state.spendForesight(5))
        assertEquals(2, state.foresight)
        assertFailsWith<IllegalArgumentException> { state.spendForesight(-5) }
        assertEquals(2, state.foresight)
    }
    @Test fun `continuous hits maintain stacks across the six second gain interval`() {
        val state = PioneerState()
        val target = UUID.randomUUID()
        state.hit(target, 0)
        for (tick in 20L..100L step 20) state.hit(target, tick)
        assertEquals(1, state.acceleration)
        state.hit(target, 120)
        assertEquals(2, state.acceleration)
        for (tick in 140L..800L step 20) state.hit(target, tick)
        assertEquals(5, state.acceleration)
    }
    @Test fun `four seconds without hitting or a different target resets accumulated acceleration`() {
        val state = PioneerState()
        val target = UUID.randomUUID()
        for (tick in 0L..240L step 20) state.hit(target, tick)
        assertEquals(3, state.acceleration)
        state.expire(319)
        assertEquals(3, state.acceleration)
        state.expire(320)
        assertEquals(0, state.acceleration)
        state.hit(target, 320)
        assertEquals(1, state.acceleration)
        state.hit(UUID.randomUUID(), 321)
        assertEquals(1, state.acceleration)
    }
    @Test fun `bullets are capped and failed followups do not consume partial ammunition`() {
        val state = PioneerState()
        state.addBullets(1)
        assertFalse(state.spendBullets(2))
        assertEquals(1, state.bullets)
        state.addBullets(20)
        assertEquals(6, state.bullets)
        repeat(3) { assertTrue(state.spendBullets(2)) }
        assertFalse(state.spendBullets(2))
        assertEquals(0, state.bullets)
    }
    @Test fun `ultimate permits five stages inside a fixed ten second window`() {
        val state = PioneerState()
        assertEquals(1, state.advanceChain(100))
        assertEquals(300L, state.chainExpires)
        for (stage in 2..5) assertEquals(stage, state.advanceChain(100L + stage))
        assertEquals(0, state.advanceChain(200))
        assertEquals(300L, state.chainExpires)
        state.endChain()
        assertEquals(0, state.chainStage)
        assertEquals(1, state.advanceChain(1000))
        assertEquals(0, state.advanceChain(1200))
    }
}
