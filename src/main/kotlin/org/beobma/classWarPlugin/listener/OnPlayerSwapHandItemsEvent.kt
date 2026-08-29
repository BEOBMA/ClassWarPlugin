package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.handler.WeaponInputHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerSwapHandItemsEvent

class OnPlayerSwapHandItemsEvent : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!isGaming() && !PlayerTagManager.isTraining(player)) return
        val playerData = findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.find { it.uniqueId == player.uniqueId } ?: return
        if (!playerData.canDispatchClassHandlers()) return
        val heldItem = player.inventory.itemInMainHand
        val taggedClassId = getWeaponClassId(heldItem)
        playerData.gameClasses
            .filter { gameClass ->
                if (taggedClassId != null) gameClass.javaClass.name == taggedClassId
                else heldItem.type == gameClass.weapon.material
            }
            .filterIsInstance<WeaponInputHandler>()
            .forEach { handler ->
                handler.onWeaponSwapHand(event)
                if (event.isCancelled) return
            }
    }
}
