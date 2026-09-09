package org.beobma.classWarPlugin.ability

import kotlin.test.*

class NewClassCatalogTest {
    @Test fun `new class factories preserve IDs and create independent skill instances`() {
        for (id in listOf("dualwield", "crossbow", "freikugel", "warcorrespondent", "pioneer", "contractor")) {
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
}
