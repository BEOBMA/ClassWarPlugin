package org.beobma.classWarPlugin.manager

import org.bukkit.Bukkit
import java.util.UUID

object PlayerListManager {
    private data class HiddenEntry(
        val viewerId: UUID,
        val targetId: UUID,
    )

    private val hiddenEntries: MutableSet<HiddenEntry> = mutableSetOf()

    fun hideAll() {
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        onlinePlayers.forEach { viewer ->
            onlinePlayers.forEach { target ->
                if (viewer.isListed(target) && viewer.unlistPlayer(target)) {
                    hiddenEntries.add(HiddenEntry(viewer.uniqueId, target.uniqueId))
                }
            }
        }
    }

    fun restoreAll() {
        hiddenEntries.forEach { entry ->
            val viewer = Bukkit.getPlayer(entry.viewerId)?.takeIf { it.isOnline } ?: return@forEach
            val target = Bukkit.getPlayer(entry.targetId)?.takeIf { it.isOnline } ?: return@forEach
            if (viewer.canSee(target)) viewer.listPlayer(target)
        }
        hiddenEntries.clear()
    }
}
