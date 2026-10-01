package org.beobma.classWarPlugin.gameClass.constellations

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.ResonanceResourceUser
import kotlin.test.*

class StarSequenceTest {
    @Test fun `ordered inventory selections yield triangular star counts up to thirty six`() {
        val slots = listOf(53,0,27,12,46,1,35,23)
        val sequence = StarSequence(slots)
        assertEquals(0, sequence.count)
        slots.forEachIndexed { index, slot ->
            assertTrue(sequence.choose(slot))
            assertEquals((index+1)*(index+2)/2, sequence.count)
        }
        assertEquals(36, sequence.count)
        assertFalse(sequence.choose(8))
    }

    @Test fun `incorrect or repeated choices preserve earned stars and permanently end this attempt`() {
        val sequence = StarSequence((0..7).toList())
        assertTrue(sequence.choose(0))
        assertTrue(sequence.choose(1))
        assertFalse(sequence.choose(1))
        assertFalse(sequence.choose(2))
        assertEquals(3, sequence.count)
        assertFailsWith<IllegalArgumentException> { StarSequence(listOf(0,1,2,3,4,5,6,54)) }
        assertFailsWith<IllegalArgumentException> { StarSequence(listOf(0,1,2,3,4,5,6,6)) }
        assertFailsWith<IllegalArgumentException> { StarSequence((0..6).toList()) }
    }

    @Test fun `new classes are enabled with distinct stable skills and correct visibility and rarity`() {
        val echo = AbilityCatalog.create("afterglow")
        val stars = AbilityCatalog.create("constellations")
        assertTrue(echo is ResonanceResourceUser)
        assertEquals(Rank.S, echo.rank)
        assertEquals(Rank.SPECIAL, stars.rank)
        assertTrue("afterglow" in AbilityCatalog.enabledClassIds())
        assertTrue("constellations" in AbilityCatalog.enabledClassIds())
        val skills = (echo.skills+stars.skills).map { it.definitionId }
        assertEquals(5, skills.toSet().size)
        assertTrue(skills.none { it.startsWith("dummy/") })
    }
}
