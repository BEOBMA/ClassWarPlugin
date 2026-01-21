package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openClassPickInventory
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.scheduler.BukkitRunnable

class OnInventoryCloseEvent : Listener {

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (PlayerTagManager.hasTag(player, "openClassPickInventory")) {
            reopenClassPickInventoryLater(
                player,
                "openClassPickInventory"
            )
            return
        }

        if (PlayerTagManager.hasTag(player, "openingClassStatusInventory")) {
            PlayerTagManager.removeTag(player, "openingClassStatusInventory")
            PlayerTagManager.removeTag(player, "openClassListInventory")
            return
        }

        if (PlayerTagManager.hasTag(player, "openClassStatusInventory")) {
            val page = PlayerTagManager.findTag(player) { it.startsWith("classListPage:") }
                ?.substringAfter("classListPage:")
                ?.toIntOrNull()
                ?: 0
            PlayerTagManager.removeTag(player, "openClassStatusInventory")
            PlayerTagManager.removeTag(player, "openingClassStatusInventory")
            PlayerTagManager.removeIf(player) { it.startsWith("classListPage:") }
            reopenClassListInventoryLater(player, page)
            return
        }

        if (PlayerTagManager.hasTag(player, "openClassListInventory")) {
            PlayerTagManager.removeTag(player, "openClassListInventory")
            PlayerTagManager.removeIf(player) { it.startsWith("classListPage:") }
            return
        }
    }

    private fun reopenClassPickInventoryLater(player: Player, tag: String) {
        object : BukkitRunnable() {
            override fun run() {
                if (PlayerTagManager.hasTag(player, tag)) {
                    val playerData = game?.playerDatas?.find { it.player == player } ?: return

                    playerData.openClassPickInventory(1)
                }
            }
        }.runTaskLater(ClassWarPlugin.instance, 10L)
    }

    private fun reopenClassListInventoryLater(player: Player, page: Int) {
        object : BukkitRunnable() {
            override fun run() {
                player.openClassListInventory(page)
            }
        }.runTaskLater(ClassWarPlugin.instance, 1L)
    }
}
