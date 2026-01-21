package org.beobma.classWarPlugin.manager

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerTagManager {
    private val tagsByPlayer = ConcurrentHashMap<UUID, MutableSet<String>>()

    private fun tags(player: Player): MutableSet<String> =
        tagsByPlayer.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }

    fun hasTag(player: Player, tag: String): Boolean = tags(player).contains(tag)

    fun addTag(player: Player, tag: String) {
        tags(player).add(tag)
    }

    fun removeTag(player: Player, tag: String) {
        tagsByPlayer[player.uniqueId]?.remove(tag)
    }

    fun removeIf(player: Player, predicate: (String) -> Boolean) {
        tagsByPlayer[player.uniqueId]?.removeIf(predicate)
    }

    fun findTag(player: Player, predicate: (String) -> Boolean): String? =
        tagsByPlayer[player.uniqueId]?.firstOrNull(predicate)

    fun allTags(player: Player): Set<String> = tagsByPlayer[player.uniqueId]?.toSet() ?: emptySet()

    fun clear(player: Player) {
        tagsByPlayer.remove(player.uniqueId)
    }
}
