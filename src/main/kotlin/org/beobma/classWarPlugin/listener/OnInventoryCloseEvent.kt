package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openAssignedClassInventory
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.Contractor
import org.beobma.classWarPlugin.gameClass.list.DeathNote
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.manager.PlayerFlag
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.PlayerTagValue
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.scheduler.BukkitRunnable

class OnInventoryCloseEvent : Listener {

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (Contractor.isGuessInventoryOpen(player)) {
            Contractor.handleInventoryClose(player)
            return
        }

        if (DeathNote.isSelectionInventoryOpen(player)) {
            DeathNote.handleInventoryClose(player)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_STARTING_ITEMS_INVENTORY)) {
            GameSettings.setStartingItems(event.inventory.contents.filterNotNull())
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_STARTING_ITEMS_INVENTORY)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_CLASS_WEAPON_INVENTORY)) {
            GameSettings.setClassWeapon(event.inventory.getItem(4))
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_CLASS_WEAPON_INVENTORY)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_GAME_MODE_INVENTORY)) {
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_GAME_MODE_INVENTORY)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPENING_ASSIGNED_CLASS_INVENTORY)) {
            PlayerTagManager.removeFlag(player, PlayerFlag.OPENING_ASSIGNED_CLASS_INVENTORY)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)) {
            reopenAssignedClassInventoryLater(player)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPENING_CONFIG_INVENTORY)) {
            PlayerTagManager.removeFlag(player, PlayerFlag.OPENING_CONFIG_INVENTORY)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_CONFIG_INVENTORY)) {
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_CONFIG_INVENTORY)
            PlayerTagManager.removeValue(player, PlayerTagValue.CONFIG_CATEGORY)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPENING_CLASS_STATUS_INVENTORY)) {
            PlayerTagManager.removeFlag(player, PlayerFlag.OPENING_CLASS_STATUS_INVENTORY)
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_CLASS_LIST_INVENTORY)
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_TRAINING_CLASS_LIST_INVENTORY)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_CLASS_STATUS_INVENTORY)) {
            val page = PlayerTagManager.getValue(player, PlayerTagValue.CLASS_LIST_PAGE)
                ?.toIntOrNull()
                ?: 0
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_CLASS_STATUS_INVENTORY)
            PlayerTagManager.removeFlag(player, PlayerFlag.OPENING_CLASS_STATUS_INVENTORY)
            PlayerTagManager.removeValue(player, PlayerTagValue.CLASS_LIST_PAGE)
            PlayerTagManager.removeValue(player, PlayerTagValue.CLASS_STATUS_RETURN)
            reopenClassListInventoryLater(player, page)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_CLASS_LIST_INVENTORY)) {
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_CLASS_LIST_INVENTORY)
            PlayerTagManager.removeValue(player, PlayerTagValue.CLASS_LIST_PAGE)
            PlayerTagManager.removeValue(player, PlayerTagValue.CLASS_STATUS_RETURN)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_TRAINING_CLASS_LIST_INVENTORY)) {
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_TRAINING_CLASS_LIST_INVENTORY)
            PlayerTagManager.removeValue(player, PlayerTagValue.CLASS_LIST_PAGE)
            PlayerTagManager.removeValue(player, PlayerTagValue.CLASS_STATUS_RETURN)
            return
        }
    }

    private fun reopenAssignedClassInventoryLater(player: Player) {
        val task = object : BukkitRunnable() {
            override fun run() {
                val currentGame = game ?: return
                if (currentGame.phase != GamePhase.CLASS_SELECTION) return
                if (!PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)) return
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
