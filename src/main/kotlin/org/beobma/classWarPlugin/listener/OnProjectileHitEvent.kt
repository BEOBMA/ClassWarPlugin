package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.list.PortalGun
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent

class OnProjectileHitEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        if (event.hitBlock == null) return
        if (PortalGun.teleportCollidedProjectile(event.entity)) event.isCancelled = true
    }
}
