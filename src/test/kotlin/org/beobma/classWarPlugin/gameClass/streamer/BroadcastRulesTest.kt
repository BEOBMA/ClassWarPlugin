package org.beobma.classWarPlugin.gameClass.streamer

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.gameClass.Rank
import kotlin.test.*

class BroadcastRulesTest {
    @Test fun `parked streamer growth configuration is ignored`() {
        val config = org.bukkit.configuration.file.YamlConfiguration()
        config.set("growth.classes.streamer.primary", "STRENGTH")
        config.set("growth.classes.creator.primary", "INTELLIGENCE")
        val settings = org.beobma.classWarPlugin.growth.GrowthSettings.read(config)
        assertFalse("streamer" in settings.profiles)
        assertTrue("creator" in settings.profiles)
        assertFalse("streamer" in org.beobma.classWarPlugin.growth.GrowthClassCatalog.styles)
    }
    @Test fun `streamer is parked and configuration cannot reenable it`() {
        val streamer = org.beobma.classWarPlugin.gameClass.list.Streamer()
        assertEquals(Rank.S, streamer.rank)
        assertEquals(listOf("streamer/red-skill"), streamer.skills.map { it.definitionId })
        assertFalse("streamer" in AbilityCatalog.enabledClassIds())
        assertFailsWith<IllegalArgumentException> { AbilityCatalog.create("streamer") }
        val balances = org.beobma.classWarPlugin.manager.ClassBalanceManager
        assertFalse(balances.isEnabled(streamer))
        balances.toggleEnabled(streamer)
        balances.adjust(streamer, org.beobma.classWarPlugin.manager.ClassBalanceField.DAMAGE, true, 1)
        balances.reset(streamer)
        assertFalse(balances.isEnabled(streamer))
    }

    @Test fun `viewer count is bounded and both decay and excitement work at zero`() {
        assertEquals(0, BroadcastRules.decay(0))
        assertEquals(0, BroadcastRules.decay(1))
        assertEquals(990, BroadcastRules.decay(1000))
        assertEquals(200, BroadcastRules.excited(0))
        assertEquals(BroadcastRules.MAX_VIEWERS, BroadcastRules.excited(BroadcastRules.MAX_VIEWERS))
    }

    @Test fun `bonus never gives immunity and donation chance never becomes guaranteed`() {
        for (viewers in listOf(-1, 0, 1000, 100000, 1000000, Int.MAX_VALUE)) {
            assertTrue(BroadcastRules.bonus(viewers) in 0.0..1.0)
            assertTrue(1.0 - BroadcastRules.bonus(viewers) / 2 in 0.5..1.0)
            assertTrue(BroadcastRules.chance(viewers) in 0.15..0.65)
        }
    }

    @Test fun `one donation activates only its highest qualifying tier`() {
        assertNull(BroadcastRules.tier(999))
        BroadcastRules.tiers.forEachIndexed { i, threshold ->
            assertEquals(threshold, BroadcastRules.tier(threshold))
            assertEquals(BroadcastRules.tiers.getOrNull(i - 1), BroadcastRules.tier(threshold - 1))
        }
        assertEquals(1000000, BroadcastRules.tier(Int.MAX_VALUE))
    }

    @Test fun `donations scale with viewers and all effect tiers are reachable`() {
        assertTrue(BroadcastRules.donation(10000, 0.5) > BroadcastRules.donation(1000, 0.5))
        assertEquals(100, BroadcastRules.donation(0, 0.0))
        for (threshold in BroadcastRules.tiers) assertEquals(threshold, BroadcastRules.donation(threshold, 1.0 / 3))
        assertEquals(2000000, BroadcastRules.donation(Int.MAX_VALUE, 1.0))
    }

    @Test fun `situational original chat pools are varied and distinct`() {
        val lines = BroadcastRules.idle + BroadcastRules.attack + BroadcastRules.hurt
        assertTrue(lines.size >= 40)
        assertEquals(lines.size, lines.distinct().size)
        assertTrue(BroadcastRules.names.size >= 10)
    }
}
