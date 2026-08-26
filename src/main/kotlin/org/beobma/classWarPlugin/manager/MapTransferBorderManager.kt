package org.beobma.classWarPlugin.manager

import org.bukkit.World
import org.bukkit.Location
import java.util.IdentityHashMap

/**
 * Temporarily opens the real world border while players are visiting an isolated ability map.
 * The first expansion remembers the live (possibly shrinking) size and the last release restores it.
 */
object MapTransferBorderManager {
    private data class ExpansionState(
        val restoreCenter: Location,
        val restoreSize: Double,
        var references: Int = 1,
    )

    private val expansions = IdentityHashMap<World, ExpansionState>()

    fun expandToMaximum(world: World): Expansion {
        val current = expansions[world]
        if (current != null) {
            current.references++
            return Expansion(world)
        }

        val border = world.worldBorder
        val restoreCenter = border.center.clone()
        val restoreSize = border.size
        border.changeSize(restoreSize, 0L)
        border.setCenter(0.0, 0.0)
        border.size = border.maxSize
        expansions[world] = ExpansionState(restoreCenter, restoreSize)
        return Expansion(world)
    }

    fun isExpanded(world: World): Boolean = expansions.containsKey(world)

    private fun restore(world: World) {
        val state = expansions[world] ?: return
        state.references--
        if (state.references > 0) return

        expansions.remove(world)
        val border = world.worldBorder
        border.setCenter(state.restoreCenter)
        border.changeSize(state.restoreSize.coerceIn(1.0, border.maxSize), 0L)
    }

    class Expansion internal constructor(private val world: World) {
        private var active = true

        fun restore() {
            if (!active) return
            active = false
            MapTransferBorderManager.restore(world)
        }
    }
}
