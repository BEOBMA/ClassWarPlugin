package org.beobma.classWarPlugin.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.GameManager.confirmAssignedClass
import org.beobma.classWarPlugin.manager.GameManager.refreshAssignedClass
import org.beobma.classWarPlugin.manager.GameManager.startTraining
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openClassStatusInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openConfigInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openConfigCategoryInventory
import org.beobma.classWarPlugin.manager.InventoryManager.getOpenConfigCategory
import org.beobma.classWarPlugin.manager.InventoryManager.getClassFromItem
import org.beobma.classWarPlugin.manager.InventoryManager.openTrainingClassListInventory
import org.beobma.classWarPlugin.manager.ConfigCategory
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.Contractor
import org.beobma.classWarPlugin.gameClass.list.DeathNote
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

class OnInventoryClickEvent : Listener {
    private val miniMessage = MiniMessage.miniMessage()
    private val pageRegex = "페이지 (\\d+)/(\\d+)".toRegex()
    private val nextPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(miniMessage.deserialize("<gray>다음 페이지"))
        }
    }.itemMeta
    private val previousPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(miniMessage.deserialize("<gray>이전 페이지"))
        }
    }.itemMeta
    private val nullItem = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(miniMessage.deserialize("<gray>비어있음"))
        }
    }.itemMeta

    @EventHandler
    fun onClickItem(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.view
        if (Contractor.isGuessInventoryOpen(player)) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            Contractor.handleInventoryClick(player, event.rawSlot)
            return
        }

        if (DeathNote.isSelectionInventoryOpen(player)) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            DeathNote.handleInventoryClick(player, event.rawSlot)
            return
        }

        if (PlayerTagManager.hasTag(player, "openConfigInventory")) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val category = getOpenConfigCategory(player)
            if (category == null) {
                val selectedCategory = when (event.rawSlot) {
                    10 -> ConfigCategory.GAME
                    12 -> ConfigCategory.RANK
                    14 -> ConfigCategory.SCATTER
                    16 -> ConfigCategory.BORDER
                    20 -> ConfigCategory.COMBAT
                    else -> null
                } ?: return
                player.openConfigCategoryInventory(selectedCategory)
                return
            }

            if (event.rawSlot == 18) {
                player.openConfigInventory()
                return
            }
            val settingSlot = configSettingSlot(category, event.rawSlot) ?: return
            GameSettings.adjust(settingSlot, event.isLeftClick, if (event.isShiftClick) 10 else 1)
            player.openConfigCategoryInventory(category)
            return
        }

        if (PlayerTagManager.hasTag(player, "openAssignedClassInventory")) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val currentGame = game ?: return
            val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
                .find { it.player == player } ?: return
            when (event.rawSlot) {
                45 -> playerData.refreshAssignedClass()
                53 -> playerData.confirmAssignedClass()
            }
            return
        }

        if (PlayerTagManager.hasTag(player, "openClassListInventory")) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val clickItem = event.currentItem ?: return
            classListHandler(player, clickItem, inventory)
            return
        }

        if (PlayerTagManager.hasTag(player, "openTrainingClassListInventory")) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val clickItem = event.currentItem ?: return
            trainingClassListHandler(player, clickItem, inventory)
            return
        }

        if (PlayerTagManager.hasTag(player, "openClassStatusInventory")) {
            event.isCancelled = true
            return
        }
    }

    private fun classListHandler(player: Player, clickItem: ItemStack, inventory: InventoryView) {
        val itemMeta = clickItem.itemMeta ?: return
        when (itemMeta) {
            previousPage -> {
                player.closeInventory()
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                player.openClassListInventory(currentPage - 1)
                return
            }

            nextPage -> {
                player.closeInventory()
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                player.openClassListInventory(currentPage + 1)
                return
            }

            nullItem -> return

            else -> {
                val gameClass = getClassFromItem(clickItem) ?: return
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                PlayerTagManager.removeIf(player) { it.startsWith("classListPage:") }
                PlayerTagManager.addTag(player, "classListPage:$currentPage")
                PlayerTagManager.removeIf(player) { it.startsWith("classStatusReturn:") }
                PlayerTagManager.addTag(player, "classStatusReturn:list")
                PlayerTagManager.addTag(player, "openingClassStatusInventory")
                player.openClassStatusInventory(gameClass)
                PlayerTagManager.addTag(player, "openClassStatusInventory")
                return
            }
        }
    }

    private fun trainingClassListHandler(
        player: Player,
        clickItem: ItemStack,
        inventory: InventoryView,
    ) {
        val itemMeta = clickItem.itemMeta ?: return
        when (itemMeta) {
            previousPage -> {
                player.closeInventory()
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                player.openTrainingClassListInventory(currentPage - 1)
                return
            }

            nextPage -> {
                player.closeInventory()
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                player.openTrainingClassListInventory(currentPage + 1)
                return
            }

            nullItem -> return

            else -> {
                val gameClass = getClassFromItem(clickItem) ?: return
                player.closeInventory()
                PlayerTagManager.removeTag(player, "openTrainingClassListInventory")
                player.startTraining(gameClass)
                return
            }
        }
    }

    private fun getCurrentPageFromTitle(title: String): Int {
        val matchResult = pageRegex.find(title) ?: return 0
        return matchResult.groupValues[1].toInt() - 1
    }

    private fun configSettingSlot(category: ConfigCategory, inventorySlot: Int): Int? = when (category) {
        ConfigCategory.GAME -> when (inventorySlot) {
            11 -> 10
            15 -> 12
            else -> null
        }

        ConfigCategory.RANK -> when (inventorySlot) {
            10 -> 37
            11 -> 38
            12 -> 39
            14 -> 41
            15 -> 42
            16 -> 43
            else -> null
        }

        ConfigCategory.SCATTER -> when (inventorySlot) {
            10 -> 14
            13 -> 16
            16 -> 28
            else -> null
        }

        ConfigCategory.BORDER -> when (inventorySlot) {
            10 -> 22
            11 -> 30
            12 -> 32
            13 -> 34
            14 -> 40
            15 -> 44
            16 -> 46
            else -> null
        }

        ConfigCategory.COMBAT -> when (inventorySlot) {
            13 -> 24
            else -> null
        }
    }
}
