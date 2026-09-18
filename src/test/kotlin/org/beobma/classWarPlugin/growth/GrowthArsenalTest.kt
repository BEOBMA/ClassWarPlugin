package org.beobma.classWarPlugin.growth

import kotlin.test.*

class GrowthArsenalTest {
    @Test fun `new builds offer distinct stat distributions with equal budgets`() {
        val builds = GrowthArsenal.builds
        assertEquals(21, builds.size)
        assertEquals(21, builds.map { it.id }.toSet().size)
        assertEquals(21, builds.map { it.stats }.toSet().size)
        assertEquals(mapOf(1 to 4, 2 to 12, 3 to 4, 4 to 1), builds.groupingBy { it.stats.size }.eachCount())
        assertTrue(builds.all { it.stats.values.sum() == 14 && it.stats.values.all { value -> value > 0 } })
    }

    @Test fun `each slot gains six families covering every build and rarity`() {
        val items = GrowthArsenal.create(GrowthItems.all.filter { !it.eventOnly })
        assertEquals(504, items.size)
        GrowthSlot.entries.forEach { slot -> assertEquals(126, items.count { it.slot == slot }) }
        for (item in items) {
            assertEquals(item, GrowthItems.byId(item.id))
            assertContains(GrowthItems.ordinary, item)
            assertContains(GrowthItems.legendary, GrowthItems.byId("${item.id}-legendary"))
            assertContains(GrowthItems.transcendent, GrowthItems.byId("${item.id}-transcendent"))
        }
    }

    @Test fun `shared effect from different slots remains a single effect with shared cooldown`() {
        val state = GrowthPlayerState()
        val items = GrowthItems.ordinary.filter { it.effect == GrowthEffect.LIFESTEAL }.distinctBy { it.slot }
        assertTrue(items.size >= 2)
        items.forEach { state.inventory.add(it.id); assertTrue(state.equip(it.id)) }
        assertTrue(state.has(GrowthEffect.LIFESTEAL))
        assertTrue(state.trigger("lifesteal", 0, 1))
        assertFalse(state.trigger("lifesteal", 0, 1))
        GrowthStat.entries.forEach { stat -> assertEquals(items.sumOf { it.stats[stat] ?: 0 }, state.stat(stat)) }
    }
}
