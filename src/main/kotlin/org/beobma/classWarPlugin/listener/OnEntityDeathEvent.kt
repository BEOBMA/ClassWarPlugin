package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.list.Levatain
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
        val creditedKiller = attribution?.attackerId ?: entity.killer?.uniqueId
        org.beobma.classWarPlugin.info.Info.game?.growth?.let { runtime ->
            if (entity.uniqueId in runtime.mobs) {
                event.drops.clear(); event.droppedExp = 0
                runtime.mobDeath(entity.uniqueId, creditedKiller)
            }
        }
        val killerId = creditedKiller ?: return
        Levatain.handleKill(killerId)
    }
}
