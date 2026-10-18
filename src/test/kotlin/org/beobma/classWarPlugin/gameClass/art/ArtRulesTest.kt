package org.beobma.classWarPlugin.gameClass.art

import org.beobma.classWarPlugin.ability.ResourceScope
import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.growth.GrowthClassCatalog
import kotlin.test.*

class ArtRulesTest {
    @Test fun `recorded casts reserve cooldown without allowing duplicate spam`() {
        val reservation = DeferredCastCooldowns()
        assertTrue(reservation.ready("skill-a", 10))
        reservation.reserve("skill-a", 10, 40)
        assertFalse(reservation.ready("skill-a", 49))
        assertTrue(reservation.ready("skill-a", 50))
        assertTrue(reservation.ready("skill-b", 11))
        reservation.reserve("zero-cooldown", 10, 0)
        assertFalse(reservation.ready("zero-cooldown", 10))
        assertTrue(reservation.ready("zero-cooldown", 11))
    }
    @Test fun `forward playback cannot refund resources or erase cooldowns each frame`() {
        assertTrue(PlaybackRules.restoreState(forward = true, initial = true))
        assertFalse(PlaybackRules.restoreState(forward = true))
        assertTrue(PlaybackRules.restoreState(forward = false))
    }
    @Test fun `paint ramps from minimum to capped maximum`() {
        assertEquals(0.01, PaintRules.damage(0))
        assertEquals(4.0, PaintRules.damage(20))
        assertEquals(4.0, PaintRules.damage(999))
        assertTrue((0..19).all { PaintRules.damage(it) < PaintRules.damage(it+1) })
    }
    @Test fun `playback visits every frame exactly once in either direction`() {
        assertEquals((0..60).toList(), PlaybackRules.indices(60, true))
        assertEquals((60 downTo 0).toList(), PlaybackRules.indices(60, false))
    }
    @Test fun `reverse clones require action and respect per target interval`() {
        assertFalse(PlaybackRules.cloneDue(10,0,10,false))
        assertFalse(PlaybackRules.cloneDue(9,0,10,true))
        assertTrue(PlaybackRules.cloneDue(10,0,10,true))
        assertTrue(PlaybackRules.cloneDue(5,0,5,true))
    }
    @Test fun `rewind removes only newer owned effects and leaves scope reusable`() {
        val resources = ResourceScope()
        val cleaned = mutableListOf<Int>()
        resources.own { cleaned += 1 }
        val checkpoint = resources.checkpoint()
        resources.own { cleaned += 2 }
        resources.own { cleaned += 3 }
        resources.rewind(checkpoint)
        resources.rewind(checkpoint)
        assertEquals(listOf(3,2), cleaned)
        assertFalse(resources.isClosed)
        resources.own { cleaned += 4 }
        resources.close()
        assertEquals(listOf(3,2,4,1), cleaned)
    }
    @Test fun `new classes participate in growth and use unique skill IDs`() {
        val ids = listOf("artist", "cartoonist", "intrusion")
        ids.forEach { assertTrue(it in GrowthClassCatalog.styles); assertTrue(it in AbilityCatalog.enabledClassIds()) }
        val skills = ids.flatMap { AbilityCatalog.create(it).skills }.map { it.definitionId }
        assertEquals(skills.size, skills.toSet().size)
        assertTrue(skills.none { it.startsWith("dummy/") })
    }
}
