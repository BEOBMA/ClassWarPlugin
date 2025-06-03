package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.InventoryManager.openClassPickInventory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.scheduler.BukkitRunnable

class OnInventoryCloseEvent : Listener {

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (player.scoreboardTags.contains("openClassPickInventory")) {
            reopenClassPickInventoryLater(
                player,
                "openClassPickInventory"
            )
            return
        }

        if (player.scoreboardTags.contains("openClassListInventory")) {
            player.scoreboardTags.remove("openClassListInventory")
            return
        }
    }

    private fun reopenClassPickInventoryLater(player: Player, tag: String) {
        object : BukkitRunnable() {
            override fun run() {
                if (player.scoreboardTags.contains(tag)) {
                    val playerData = game?.playerDatas?.find { it.player == player } ?: return

                    playerData.openClassPickInventory(1)
                }
            }
        }.runTaskLater(ClassWarPlugin.instance, 10L)
    }
}