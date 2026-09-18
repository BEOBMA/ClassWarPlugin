package org.beobma.classWarPlugin.growth

import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.*

class GrowthExpansionTest {
    private fun bundledConfig() = javaClass.getResourceAsStream("/config.yml")!!.reader().use {
        YamlConfiguration.loadConfiguration(it)
    }

    @Test fun `constructor reader bundled configuration and menu agree on increased defaults`() {
        for (settings in listOf(GrowthSettings(), GrowthSettings.read(YamlConfiguration()), GrowthSettings.read(bundledConfig()))) {
            assertEquals(12, settings.mobsPerRegion)
            assertEquals(192, settings.maximumMobs)
            assertEquals(0.45, settings.dropChance)
        }
        assertEquals(12, GrowthMenu.settings.first { it.first == "mobs.per-region" }.third)
        assertEquals(192, GrowthMenu.settings.first { it.first == "mobs.maximum" }.third)
    }

    @Test fun `old defaults upgrade once even with bundled defaults attached`() {
        val config = YamlConfiguration()
        config.setDefaults(bundledConfig())
        config.set("growth.mobs.per-region", 4)
        config.set("growth.mobs.maximum", 96)
        config.set("growth.items.drop-chance", 0.18)
        assertTrue(GrowthSettings.upgradePopulationDefaults(config))
        assertEquals(12, config.getInt("growth.mobs.per-region"))
        assertEquals(192, config.getInt("growth.mobs.maximum"))
        assertEquals(0.45, config.getDouble("growth.items.drop-chance"))
        config.set("growth.mobs.per-region", 4)
        assertFalse(GrowthSettings.upgradePopulationDefaults(config))
        assertEquals(4, config.getInt("growth.mobs.per-region"))
    }

    @Test fun `custom and disabled population settings survive migration`() {
        val config = YamlConfiguration()
        config.set("growth.mobs.per-region", 0)
        config.set("growth.mobs.maximum", 250)
        config.set("growth.items.drop-chance", 0.9)
        assertTrue(GrowthSettings.upgradePopulationDefaults(config))
        val settings = GrowthSettings.read(config)
        assertEquals(0, settings.mobsPerRegion)
        assertEquals(250, settings.maximumMobs)
        assertEquals(0.9, settings.dropChance)
    }

    @Test fun `population cap distributes fairly across regions and handles empty regions`() {
        val regions = List(16) { region -> List(12) { "$region:$it" } }
        assertEquals(192, GrowthPopulation.distribute(regions, 192).size)
        val capped = GrowthPopulation.distribute(regions, 96)
        assertEquals(16, capped.groupBy { it.substringBefore(':') }.size)
        assertTrue(capped.groupBy { it.substringBefore(':') }.values.all { it.size == 6 })
        assertEquals(listOf(1, 4, 2, 5), GrowthPopulation.distribute(listOf(listOf(1, 2, 3), emptyList(), listOf(4, 5)), 4))
        assertTrue(GrowthPopulation.distribute(regions, 0).isEmpty())
        assertTrue(GrowthPopulation.distribute(emptyList<List<Int>>(), 192).isEmpty())
    }

    @Test fun `tiered catalog expands every slot and keeps high rarity out of ordinary drops`() {
        assertEquals(3454, GrowthItems.all.size)
        assertEquals(3454, GrowthItems.all.map { it.id }.toSet().size)
        assertEquals(3454, GrowthItems.all.map { it.name }.toSet().size)
        GrowthSlot.entries.forEach { slot -> assertEquals(if (slot == GrowthSlot.RELIC) 862 else 864, GrowthItems.all.count { it.slot == slot }) }
        assertEquals(1150, GrowthItems.ordinary.size)
        assertEquals(1152, GrowthItems.legendary.size)
        assertEquals(1152, GrowthItems.transcendent.size)
        assertTrue(GrowthItems.all.filter { it.eventOnly }.all { it.rarity != GrowthRarity.HEROIC })
        assertTrue(GrowthItems.ordinary.all { it.stats.values.sum() in 12..14 && it.description.isNotBlank() })
        assertTrue(GrowthItems.all.filter { it.slot == GrowthSlot.ARMOR }.all { it.material.name.endsWith("_CHESTPLATE") })
    }

    @Test fun `every new item equips and contributes only its own stats and effect`() {
        for (item in GrowthItems.all) {
            val state = GrowthPlayerState()
            state.inventory.add(item.id)
            assertTrue(state.equip(item.id))
            assertTrue(state.has(item.effect))
            GrowthStat.entries.forEach { assertEquals(item.stats[it] ?: 0, state.stat(it)) }
            assertTrue(state.equip(item.id))
            assertTrue(state.equipment.isEmpty())
            assertFalse(state.has(item.effect))
        }
    }

    @Test fun `all equipment is reachable across pages without occupying navigation slots`() {
        val ids = GrowthItems.all.map { it.id }.toSet() + "invalid-id"
        assertEquals(77, GrowthItems.pageCount(ids))
        val first = GrowthItems.page(ids, 0)
        val pages = (0 until GrowthItems.pageCount(ids)).map { GrowthItems.page(ids, it) }
        assertEquals(45, first.size)
        assertEquals(List(76) { 45 } + 34, pages.map { it.size })
        assertEquals(GrowthItems.all, pages.flatten())
        assertEquals(first, GrowthItems.page(ids, -1))
        assertEquals(pages.last(), GrowthItems.page(ids, Int.MAX_VALUE))
        assertEquals(1, GrowthItems.pageCount(emptySet()))
        assertTrue(GrowthItems.page(emptySet(), 99).isEmpty())
    }
}
