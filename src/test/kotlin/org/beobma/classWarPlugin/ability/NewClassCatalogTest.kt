package org.beobma.classWarPlugin.ability

import kotlin.test.*

class NewClassCatalogTest {
    @Test fun `new class factories preserve IDs and create independent skill instances`() {
        for (id in listOf("crossbow", "freikugel", "warcorrespondent", "pioneer", "contractor", "agent", "writer", "metronome")) {
            val first = AbilityCatalog.create(id)
            val second = AbilityCatalog.create(id)
            assertEquals(id, first.classId)
            assertNotSame(first, second)
            assertEquals(first.skills.size, first.skills.map { it.definitionId }.distinct().size)
            first.skills.zip(second.skills).forEach { (a, b) ->
                assertNotSame(a, b)
                assertEquals(a.definitionId, b.definitionId)
            }
        }
    }

    @Test fun `removed dual wield is absent from enabled classes and factories`() {
        assertFalse("dualwield" in AbilityCatalog.enabledClassIds())
        assertFailsWith<IllegalArgumentException> { AbilityCatalog.create("dualwield") }
    }

    @Test fun `writer is available in the normal class pool`() {
        assertTrue("writer" in AbilityCatalog.enabledClassIds())
    }
}
