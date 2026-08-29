package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.list.PortalGun
import org.beobma.classWarPlugin.gameClass.list.RainbowBridge
import org.beobma.classWarPlugin.gameClass.list.Sagittarius
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent

class OnProjectileHitEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        Sagittarius.handleArrowHit(event)
        if (RainbowBridge.handleProjectileHit(event)) return
        if (event.hitBlock == null) return
        if (PortalGun.teleportCollidedProjectile(event.entity)) event.isCancelled = true
    }
}
