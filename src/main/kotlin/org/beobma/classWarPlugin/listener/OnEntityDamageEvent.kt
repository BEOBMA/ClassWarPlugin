package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent

class OnEntityDamageEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity.isMannequin()) {
            event.isCancelled = true
        }
        val player = event.entity as? Player ?: return
        if (!PlayerTagManager.hasTag(player, "isTraining")) {
            return
        }

        val finalDamage = event.finalDamage
        if (finalDamage > 0.0) {
            val formattedDamage = String.format("%.2f", finalDamage)
            player.sendMiniMessage("<red>받은 피해 정보 - <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
        }
        event.isCancelled = true
    }
}
