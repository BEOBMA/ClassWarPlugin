package org.beobma.classWarPlugin.growth

import kotlin.random.Random
import kotlin.test.*

class GrowthRarityTest {
    @Test fun `ordinary drops never promote and limited drops follow ninety ten distribution`() {
        val random = Random(42)
        repeat(10000) { assertEquals(GrowthRarity.HEROIC, GrowthItems.monsterReward(false, random).rarity) }
        val counts = List(100000) { GrowthItems.monsterReward(true, random).rarity }.groupingBy { it }.eachCount()
        assertEquals(setOf(GrowthRarity.LEGENDARY, GrowthRarity.TRANSCENDENT), counts.keys)
        assertTrue(counts.getValue(GrowthRarity.TRANSCENDENT) in 9500..10500)
    }

    @Test fun `promotion changes stats while preserving slot effect and original identity`() {
        for (hero in GrowthItems.ordinary) {
            val legend = assertNotNull(GrowthItems.byId("${hero.id}-legendary"))
            val transcendent = assertNotNull(GrowthItems.byId("${hero.id}-transcendent"))
            for (upgrade in listOf(legend, transcendent)) {
                assertEquals(hero.slot, upgrade.slot)
                assertEquals(hero.effect, upgrade.effect)
                assertEquals(hero.description, upgrade.description)
                assertEquals(hero.stats.keys, upgrade.stats.keys)
            }
            hero.stats.forEach { (stat, value) ->
                assertTrue(legend.stats.getValue(stat) > value)
                assertTrue(transcendent.stats.getValue(stat) > legend.stats.getValue(stat))
            }
        }
        for (id in listOf("world-tree", "moon-heart")) {
            assertEquals(GrowthRarity.LEGENDARY, GrowthItems.byId(id)!!.rarity)
            assertEquals(20, GrowthItems.byId(id)!!.stats.values.sum())
            assertEquals(30, GrowthItems.byId("$id-transcendent")!!.stats.values.sum())
        }
        assertContains(GrowthItems.ordinary.first().displayName, "<light_purple>[영웅]")
        assertContains(GrowthItems.legendary.first().displayName, "<yellow>[전설]")
        assertContains(GrowthItems.transcendent.first().displayName, "<red>[초월]")
    }
}
