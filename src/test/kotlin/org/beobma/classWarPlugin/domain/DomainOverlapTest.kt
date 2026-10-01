package org.beobma.classWarPlugin.domain

import kotlin.test.*
import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.bukkit.Material

class DomainOverlapTest {
    @Test fun `only spatially intersecting spheres clash`() {
        assertTrue(DomainOverlap.intersects(30.0,0.0,0.0,25,10))
        assertFalse(DomainOverlap.intersects(35.0,0.0,0.0,25,10))
        assertFalse(DomainOverlap.intersects(0.0,36.0,0.0,25,10))
        assertTrue(DomainOverlap.intersects(0.0,0.0,0.0,10,10))
    }
    @Test fun `transitive clash splits when bridging domain disappears`() {
        val positions = mapOf("A" to 0.0,"B" to 18.0,"C" to 36.0,"D" to 100.0)
        fun overlap(a: String,b: String) = DomainOverlap.intersects(positions.getValue(a)-positions.getValue(b),0.0,0.0,10,10)
        assertEquals(setOf("A","B","C"),DomainOverlap.component("A",positions.keys.toList(),::overlap))
        assertEquals(setOf("A"),DomainOverlap.component("A",listOf("A","C","D"),::overlap))
        assertEquals(setOf("B","C"),DomainOverlap.component("B",listOf("B","C","D"),::overlap))
    }
    @Test fun `domain skills use a crystal and other skills keep dyes`() {
        for (gameClass in listOf(AbilityCatalog.create("creator"),AbilityCatalog.create("constellations"),
            org.beobma.classWarPlugin.gameClass.list.Referee())) {
            val skills = gameClass.skills
            val domain = skills.single { it.isDomainExpansion }
            assertEquals(Material.END_CRYSTAL,domain.itemMaterial)
            assertNull(skills.first().itemMaterial)
        }
    }
}
