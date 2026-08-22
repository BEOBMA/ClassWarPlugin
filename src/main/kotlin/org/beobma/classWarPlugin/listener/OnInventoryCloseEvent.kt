package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openAssignedClassInventory
import org.beobma.classWarPlugin.entity.player.PlayerData
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

        if (PlayerTagManager.hasTag(player, "openingAssignedClassInventory")) {
            PlayerTagManager.removeTag(player, "openingAssignedClassInventory")
            return
        }

        if (PlayerTagManager.hasTag(player, "openAssignedClassInventory")) {
            reopenAssignedClassInventoryLater(player)
            return
        }

        if (PlayerTagManager.hasTag(player, "openingConfigInventory")) {
            PlayerTagManager.removeTag(player, "openingConfigInventory")
            return
        }

        if (PlayerTagManager.hasTag(player, "openConfigInventory")) {
            PlayerTagManager.removeTag(player, "openConfigInventory")
            PlayerTagManager.removeIf(player) { it.startsWith("configCategory:") }
            return
        }

        if (PlayerTagManager.hasTag(player, "openingClassStatusInventory")) {
            PlayerTagManager.removeTag(player, "openingClassStatusInventory")
            PlayerTagManager.removeTag(player, "openClassListInventory")
            PlayerTagManager.removeTag(player, "openTrainingClassListInventory")
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
            PlayerTagManager.removeIf(player) { it.startsWith("classStatusReturn:") }
            reopenClassListInventoryLater(player, page)
            return
        }

        if (PlayerTagManager.hasTag(player, "openClassListInventory")) {
            PlayerTagManager.removeTag(player, "openClassListInventory")
            PlayerTagManager.removeIf(player) { it.startsWith("classListPage:") }
            PlayerTagManager.removeIf(player) { it.startsWith("classStatusReturn:") }
            return
        }

        if (PlayerTagManager.hasTag(player, "openTrainingClassListInventory")) {
            PlayerTagManager.removeTag(player, "openTrainingClassListInventory")
            PlayerTagManager.removeIf(player) { it.startsWith("classListPage:") }
            PlayerTagManager.removeIf(player) { it.startsWith("classStatusReturn:") }
            return
        }
    }

    private fun reopenAssignedClassInventoryLater(player: Player) {
        val task = object : BukkitRunnable() {
            override fun run() {
                val currentGame = game ?: return
                if (currentGame.phase != GamePhase.CLASS_SELECTION) return
                if (!PlayerTagManager.hasTag(player, "openAssignedClassInventory")) return
                if (currentGame.confirmedPlayers.contains(player.uniqueId)) return
                val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
                    .find { it.player == player } ?: return
                playerData.openAssignedClassInventory()
            }
        }.runTaskLater(ClassWarPlugin.instance, 2L)
        game?.playerDatas?.filterIsInstance<PlayerData>()?.find { it.player == player }?.trackTask(task)
    }

    private fun reopenClassListInventoryLater(player: Player, page: Int) {
        val task = object : BukkitRunnable() {
            override fun run() {
                player.openClassListInventory(page)
            }
        }.runTaskLater(ClassWarPlugin.instance, 1L)
        game?.playerDatas?.filterIsInstance<PlayerData>()?.find { it.player == player }?.trackTask(task)
    }
}
