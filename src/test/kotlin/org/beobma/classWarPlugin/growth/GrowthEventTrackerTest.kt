package org.beobma.classWarPlugin.growth

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import java.util.UUID
import kotlin.test.*

class GrowthEventTrackerTest {
    private fun definition(id: String = "relic", mob: EntityType? = null) =
        GrowthEventDefinition(id, 2, false, emptySet(), "world-tree", 90, mob)

    @Test fun `item and monster forecasts use fixed coordinates and activate exactly once`() {
        for (type in listOf(null, EntityType.SKELETON)) {
            val tracker = GrowthEventTracker()
            val loc = Location(null, 20.5, 64.0, -30.5)
            tracker.plan(definition(mob = type), 3, loc)
            loc.x = 999.0
            val pending = tracker.visible(0).single()
            assertEquals(20.5, pending.location.x)
            assertEquals(GrowthEventState.SCHEDULED, pending.state)
            assertTrue(tracker.takeDue(1).isEmpty())
            val due = tracker.takeDue(2).single()
            assertEquals(pending.location, due.location)
            assertTrue(tracker.visible(2).isEmpty())
            val entity = UUID.randomUUID()
            tracker.activate(due.definition.id, entity)
            assertEquals(GrowthEventState.ACTIVE, tracker.visible(2).single().state)
            assertTrue(tracker.takeDue(2).isEmpty())
            tracker.resolve(entity) // item pickup or monster death uses the same terminal transition
            assertTrue(tracker.visible(2).isEmpty())
            assertTrue(tracker.visible(3).isEmpty())
            assertTrue(tracker.takeDue(2).isEmpty())
            assertFailsWith<IllegalStateException> { tracker.activate(due.definition.id, UUID.randomUUID()) }
        }
    }

    @Test fun `failed and skipped spawns leave no stale marker`() {
        val tracker = GrowthEventTracker()
        tracker.plan(definition(), 1, Location(null, 1.0, 64.0, 1.0))
        assertEquals(1, tracker.takeDue(2).size)
        assertTrue(tracker.visible(2).isEmpty()) // blocked advertised position: do not activate
        assertTrue(tracker.takeDue(2).isEmpty())
        val skipped = GrowthEventTracker()
        skipped.plan(definition(), 1, Location(null, 1.0, 64.0, 1.0))
        assertTrue(skipped.takeDue(3).isEmpty())
        assertTrue(skipped.visible(3).isEmpty())
    }

    @Test fun `removing one objective preserves other future and active objectives`() {
        val tracker = GrowthEventTracker()
        val loc = Location(null, 0.0, 64.0, 0.0)
        tracker.plan(definition("first"), 0, loc)
        tracker.plan(definition("second", EntityType.ZOMBIE), 0, loc)
        tracker.plan(definition("future").copy(day = 3), 0, loc)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        tracker.takeDue(2)
        tracker.activate("first", first)
        tracker.activate("second", second)
        tracker.resolve(first)
        tracker.resolve(first)
        tracker.resolve(UUID.randomUUID())
        assertEquals(listOf("second", "future"), tracker.visible(2).map { it.definition.id })
        tracker.clear()
        assertTrue(tracker.visible(0).isEmpty())
    }

    @Test fun `configuration preserves item events and accepts only safe monster types`() {
        val config = YamlConfiguration()
        config.set("growth.events.item.reward", "world-tree")
        config.set("growth.events.monster.reward", "moon-heart")
        config.set("growth.events.monster.mob-type", " skeleton ")
        config.set("growth.events.monster.day", 3)
        config.set("growth.events.monster.night", true)
        config.set("growth.events.invalid.mob-type", "ENDER_DRAGON")
        val events = GrowthSettings.read(config).events.associateBy { it.id }
        assertEquals(setOf("item", "monster"), events.keys)
        assertNull(events.getValue("item").mobType)
        assertEquals(EntityType.SKELETON, events.getValue("monster").mobType)
        assertEquals(5, events.getValue("monster").phaseIndex)
        assertTrue(GrowthEventDefinition.defaults.all { it.mobType == null })
    }
}
