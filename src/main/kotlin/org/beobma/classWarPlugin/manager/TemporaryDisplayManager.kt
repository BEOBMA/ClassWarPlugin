package org.beobma.classWarPlugin.manager

import org.bukkit.World
import org.bukkit.entity.Display
import java.util.UUID

object TemporaryDisplayManager {
    private fun ownerTag(ownerId: UUID): String = "cw-${ownerId.toString().take(8)}"

    fun mark(display: Display, ownerId: UUID) {
        display.addScoreboardTag(ownerTag(ownerId))
        display.isPersistent = false
    }

    fun clear(world: World, ownerId: UUID) {
        val tag = ownerTag(ownerId)
        world.getEntitiesByClass(Display::class.java)
            .filter { tag in it.scoreboardTags }
            .forEach(Display::remove)
    }
}
