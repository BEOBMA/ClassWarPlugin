package org.beobma.classWarPlugin.game

import java.util.UUID

/** Per-player offers for one match; record only assignments actually shown to the player. */
class ClassSelectionHistory {
    private val seen = mutableMapOf<UUID, MutableSet<String>>()

    fun record(player: UUID, classIds: Collection<String>) {
        seen.getOrPut(player) { mutableSetOf() }.addAll(classIds)
    }

    fun exclusions(player: UUID, current: Collection<String>, excludePrevious: Boolean): Set<String> =
        current.toSet() + if (excludePrevious) seen[player].orEmpty() else emptySet()

    fun clear() = seen.clear()
}
