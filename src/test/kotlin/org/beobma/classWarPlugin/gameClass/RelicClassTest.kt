package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.gameClass.handler.ResonanceResourceUser
import org.beobma.classWarPlugin.growth.GrowthClassCatalog
import kotlin.test.*

class RelicClassTest {
    @Test fun `new classes are registered with unique skills and growth coverage`() {
        val classes=listOf("flashbang","gungnir","mjolnir").map(AbilityCatalog::create)
        assertEquals(listOf(Rank.C,Rank.S,Rank.S),classes.map { it.rank })
        classes.forEach { assertTrue(it.classId in AbilityCatalog.enabledClassIds()); assertTrue(it.classId in GrowthClassCatalog.styles) }
        assertEquals(1,classes.first().passives.size)
        assertEquals(setOf("gungnir/recall","mjolnir/tesla"),classes.flatMap { it.skills }.map { it.definitionId }.toSet())
        assertTrue(classes.last() is ResonanceResourceUser)
    }
}
