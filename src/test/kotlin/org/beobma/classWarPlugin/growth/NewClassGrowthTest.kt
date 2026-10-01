package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.description.GrowthDescription
import kotlin.test.*

class NewClassGrowthTest {
    private val ids = listOf("hunter", "sturmtruppe", "spezialeinheitsmitglied", "schwerekavallerie",
        "firearmsmaster", "creator", "constellations", "afterglow", "flashbang", "gungnir", "mjolnir")

    @Test fun `new playable classes have growing damage and utility profiles`() {
        for (id in ids) {
            assertTrue(id in GrowthClassCatalog.styles, id)
            assertFalse(AbilityCatalog.isDeferred(id), id)
            val profile = GrowthProfile.forClass(id)
            for (axis in GrowthAxis.entries) {
                assertEquals(1.0, profile.multiplier(axis) { 0 }, "$id $axis")
                assertTrue(profile.multiplier(axis) { 50 } > 1.0, "$id $axis")
            }
        }
        assertEquals(GrowthStat.INTELLIGENCE, GrowthProfile.forClass("creator").primary)
        assertEquals(GrowthStat.INTELLIGENCE, GrowthProfile.forClass("constellations").primary)
        assertEquals(GrowthStat.AGILITY, GrowthProfile.forClass("afterglow").primary)
        assertEquals(GrowthStat.INTELLIGENCE, GrowthProfile.forClass("mjolnir").secondary)
        assertTrue(AbilityCatalog.isDeferred("streamer"))
        assertTrue(AbilityCatalog.isDeferred("referee"))
    }

    @Test fun `new class lore bindings render in classic and growth mode`() {
        for (id in ids) {
            val ability = AbilityCatalog.create(id)
            val lines = ability.weapon.description + ability.skills.flatMap { it.description } + ability.passives.flatMap { it.description }
            val context = GrowthDescription.Context(id, GrowthProfile.forClass(id), { 50 })
            for (line in lines) {
                assertFalse(GrowthDescription.render(line, null).contains("{g:"), "$id $line")
                assertFalse(GrowthDescription.render(line, context).contains("{g:"), "$id $line")
            }
        }
    }
}
