package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.DamageIndicatorManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent

class OnDamageIndicatorEvent : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFinalPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val currentGame = game ?: return
        val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.uniqueId == player.uniqueId } ?: return
        if (playerData.entityStatus.isDead) return
        DamageIndicatorManager.show(player, event.finalDamage, currentGame.settings.damageIndicatorsEnabled)
    }
}
