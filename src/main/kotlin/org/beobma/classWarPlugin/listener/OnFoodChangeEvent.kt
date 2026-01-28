package org.beobma.classWarPlugin.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.FoodLevelChangeEvent

class OnFoodChangeEvent : Listener {
    @EventHandler
    fun onFoodChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return

        event.isCancelled = true

        player.foodLevel = 20
        player.saturation = 20f
        player.exhaustion = 0f
    }
}