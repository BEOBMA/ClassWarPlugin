package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.list.Uranus
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent

class OnEntityShootBowEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        Uranus.handleBowShot(event)
    }
}
