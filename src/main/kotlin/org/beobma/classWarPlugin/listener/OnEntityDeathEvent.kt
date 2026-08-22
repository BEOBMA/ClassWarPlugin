package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.list.AreaDevelopment
import org.beobma.classWarPlugin.manager.DamageManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent

class OnEntityDeathEvent : Listener {
    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity is Player) return

        val attribution = DamageManager.consumeAttribution(entity)
        val killerId = attribution?.attackerId ?: entity.killer?.uniqueId ?: return
        val deathCenter = entity.boundingBox.center.toLocation(entity.world)
        AreaDevelopment.handleEntityDeath(entity.uniqueId, deathCenter, killerId)
    }
}
