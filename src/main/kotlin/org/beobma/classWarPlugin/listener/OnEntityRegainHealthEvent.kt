package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.CombatManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent

class OnEntityRegainHealthEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        if (event.regainReason != EntityRegainHealthEvent.RegainReason.SATIATED &&
            event.regainReason != EntityRegainHealthEvent.RegainReason.REGEN
        ) return

        val player = event.entity as? Player ?: return
        if (CombatManager.blocksNaturalRegeneration(player)) event.isCancelled = true
    }
}
