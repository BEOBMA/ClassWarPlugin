package org.beobma.classWarPlugin.growth

import org.bukkit.Location
import java.util.UUID

enum class GrowthEventState { SCHEDULED, ACTIVE, RESOLVED }

/** Match-owned, one-shot objectives. Resolved objectives can never return to the map. */
class GrowthEventTracker {
    data class Entry(val definition: GrowthEventDefinition, val regionId: Int, val location: Location,
        var state: GrowthEventState = GrowthEventState.SCHEDULED, var entityId: UUID? = null)
    private val entries = linkedMapOf<String, Entry>()

    fun plan(definition: GrowthEventDefinition, regionId: Int, location: Location) {
        check(definition.id !in entries)
        entries[definition.id] = Entry(definition, regionId, location.clone())
    }

    /** Reserve before spawning so failed/duplicate spawn attempts cannot leave phantom forecasts. */
    fun takeDue(phase: Int): List<Entry> = entries.values.filter {
        it.state == GrowthEventState.SCHEDULED && it.definition.phaseIndex <= phase
    }.onEach { it.state = GrowthEventState.RESOLVED }.filter { it.definition.phaseIndex == phase }

    fun activate(id: String, entityId: UUID) {
        val entry = entries.getValue(id)
        check(entry.state == GrowthEventState.RESOLVED && entry.entityId == null)
        entry.entityId = entityId
        entry.state = GrowthEventState.ACTIVE
    }

    fun resolve(entityId: UUID) {
        entries.values.firstOrNull { it.entityId == entityId }?.state = GrowthEventState.RESOLVED
    }

    fun visible(phase: Int): List<Entry> = entries.values.filter {
        it.state == GrowthEventState.ACTIVE ||
            (it.state == GrowthEventState.SCHEDULED && it.definition.phaseIndex >= phase)
    }

    fun clear() = entries.clear()
}

data class GrowthEventMarker(val name: String, val location: Location, val active: Boolean, val monster: Boolean,
    val rarity: GrowthRarity = GrowthRarity.LEGENDARY)

/** WARNING regions are still playable until the next transition, just like normal mob camps. */
fun GrowthEventDefinition.canSpawnIn(region: GrowthRegion): Boolean =
    region.state != RegionState.FORBIDDEN && (terrainTags.isEmpty() || region.terrain in terrainTags)
