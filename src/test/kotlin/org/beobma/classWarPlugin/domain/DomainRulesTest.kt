package org.beobma.classWarPlugin.domain

import org.beobma.classWarPlugin.ability.EffectTimer
import org.beobma.classWarPlugin.ability.Control
import org.beobma.classWarPlugin.ability.ControlLocks
import kotlin.test.*

class DomainRulesTest {
    @Test fun `cast uses monotonic seconds independent of combat ticks`() {
        val start = 900_000_000L
        val timeline = DomainTimeline(start, 600, 1800)
        assertFalse(timeline.castComplete(start + 2_999_000_000L))
        assertTrue(timeline.castComplete(start + 3_000_000_000L))
        assertFalse(timeline.fightStarted(start + 30_000_000_000L))
        timeline.beginTitle(start + 3_000_000_000L)
        assertFalse(timeline.subtitleVisible(start + 3_599_000_000L))
        assertTrue(timeline.subtitleVisible(start + 3_600_000_000L))
        assertFalse(timeline.fightStarted(start + 4_799_000_000L))
        assertTrue(timeline.fightStarted(start + 4_800_000_000L))
    }

    @Test fun `lag during formation cannot consume title time or combat duration`() {
        val timeline = DomainTimeline(0, 600, 1800)
        assertTrue(timeline.castComplete(15_000_000_000L))
        timeline.beginTitle(15_000_000_000L)
        timeline.beginTitle(16_000_000_000L)
        assertFalse(timeline.subtitleVisible(15_500_000_000L))
        assertTrue(timeline.subtitleVisible(15_600_000_000L))
        assertFalse(timeline.fightStarted(16_799_000_000L))
        assertTrue(timeline.fightStarted(16_800_000_000L))
    }

    @Test fun `distortion slows both delayed and already running repeating abilities twentyfold`() {
        val timer = EffectTimer(1, 2)
        repeat(19) { assertFalse(timer.advance(false, 0.05)) }
        assertTrue(timer.advance(false, 0.05))
        repeat(39) { assertFalse(timer.advance(false, 0.05)) }
        assertTrue(timer.advance(false, 0.05))
        repeat(100) { assertFalse(timer.advance(true, 0.05)) }
        assertFalse(timer.advance(false))
        assertTrue(timer.advance(false))
        val delayed = EffectTimer(2, null)
        repeat(20) { assertFalse(delayed.advance(false, 0.05)) }
        assertTrue(delayed.advance(false))
        assertTrue(delayed.complete)
        assertFalse(delayed.advance(false))
    }

    @Test fun `boundary prevents tunneling across sphere and seals floor`() {
        val boundary = DomainBoundary(10.0)
        assertTrue(boundary.contains(0.0, 0.0, 0.0))
        assertFalse(boundary.contains(10.0, 0.0, 0.0))
        assertFalse(boundary.contains(0.0, -1.01, 0.0))
        assertTrue(boundary.crosses(-20.0, 1.0, 0.0, 20.0, 1.0, 0.0))
        assertTrue(boundary.crosses(0.0, -20.0, 0.0, 0.0, 20.0, 0.0))
        assertFalse(boundary.crosses(-20.0, 11.0, 0.0, 20.0, 11.0, 0.0))
        assertFalse(boundary.crosses(-20.0, -2.0, 0.0, 20.0, -2.0, 0.0))
        assertFalse(boundary.crosses(11.0, 0.0, 0.0, 11.0, 0.0, 0.0))
    }

    @Test fun `releasing cast restriction preserves unrelated locks and vulnerability`() {
        val locks = ControlLocks()
        val stun = locks.acquire(Control.MOVE)
        val cast = locks.acquire(Control.MOVE, Control.SKILL, Control.ATTACK)
        assertFalse(locks.blocks(Control.ATTACKABLE))
        assertFalse(locks.blocks(Control.TARGETABLE))
        cast.close(); cast.close()
        assertTrue(locks.blocks(Control.MOVE))
        assertFalse(locks.blocks(Control.SKILL))
        assertFalse(locks.blocks(Control.ATTACK))
        stun.close()
        assertFalse(locks.blocks(Control.MOVE))
    }

    @Test fun `reusable interiors remain inside their own domain and leave center aisle`() {
        for (radius in 4..24) for (plan in listOf(DomainInteriors.courtroom(radius), DomainInteriors.ruinedCity(radius))) {
            assertTrue(plan.size <= 8192)
            assertTrue(plan.all { it.y in 0 until radius && it.x * it.x + it.y * it.y + it.z * it.z < (radius - 1) * (radius - 1) })
            assertTrue(plan.none { it.x == 0 && it.z == 0 })
        }
    }
}
