package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerSwapHandItemsEvent

class OnPlayerSwapHandItemsEvent : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!isGaming() && !PlayerTagManager.hasTag(player, "isTraining")) return
        val playerData = findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.find { it.uniqueId == player.uniqueId } ?: return
        val gameClass = playerData.gameClass
        if (gameClass !is WeaponInputHandler || player.inventory.itemInMainHand.type != gameClass.weapon.material) return
        gameClass.onWeaponSwapHand(event)
    }
}
