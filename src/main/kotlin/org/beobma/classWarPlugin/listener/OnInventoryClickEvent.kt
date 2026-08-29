package org.beobma.classWarPlugin.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.game.GameSetting
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.GameManager.confirmAssignedClass
import org.beobma.classWarPlugin.manager.GameManager.refreshAssignedClass
import org.beobma.classWarPlugin.manager.GameManager.startTraining
import org.beobma.classWarPlugin.manager.GameManager.startNewGame
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openClassStatusInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openConfigInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openConfigCategoryInventory
import org.beobma.classWarPlugin.manager.InventoryManager.getOpenConfigCategory
import org.beobma.classWarPlugin.manager.InventoryManager.getClassFromItem
import org.beobma.classWarPlugin.manager.InventoryManager.getMatchModeFromItem
import org.beobma.classWarPlugin.manager.InventoryManager.openTrainingClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openClassBalanceListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openClassBalanceDetailInventory
import org.beobma.classWarPlugin.manager.InventoryManager.getOpenClassBalancePage
import org.beobma.classWarPlugin.manager.InventoryManager.getSelectedClassBalance
import org.beobma.classWarPlugin.manager.InventoryManager.getDamageMultiplierTypeFromSlot
import org.beobma.classWarPlugin.manager.ConfigCategory
import org.beobma.classWarPlugin.manager.ClassBalanceField
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerFlag
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.PlayerTagValue
import org.beobma.classWarPlugin.manager.PlayerManager.refreshClassItemDescriptions
import org.beobma.classWarPlugin.manager.PlayerPreferenceManager
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.Contractor
import org.beobma.classWarPlugin.gameClass.list.DeathNote
import org.beobma.classWarPlugin.gameClass.Rank
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

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_GAME_MODE_INVENTORY)) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val matchMode = event.currentItem?.let(::getMatchModeFromItem) ?: return
            PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_GAME_MODE_INVENTORY)
            player.closeInventory()
            if (!player.isOp) {
                player.sendMessage(miniMessage.deserialize("<red><bold>[!] 이 명령어는 관리자만 사용할 수 있습니다."))
                return
            }
            val error = startNewGame(matchMode)
            if (error != null) {
                player.sendMessage(miniMessage.deserialize("<red><bold>[!] $error"))
            }
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_CONFIG_INVENTORY)) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val category = getOpenConfigCategory(player)
            if (category == null) {
                val selectedCategory = when (event.rawSlot) {
                    22 -> ConfigCategory.PERSONAL
                    10 -> ConfigCategory.GAME
                    12 -> ConfigCategory.RANK
                    14 -> ConfigCategory.SCATTER
                    16 -> ConfigCategory.BORDER
                    18 -> ConfigCategory.DAMAGE
                    20 -> ConfigCategory.COMBAT
                    24 -> ConfigCategory.CLASS_BALANCE
                    else -> null
                } ?: return
                if (selectedCategory != ConfigCategory.PERSONAL && !player.isOp) return
                player.openConfigCategoryInventory(selectedCategory)
                return
            }

            if (category == ConfigCategory.CLASS_BALANCE) {
                handleClassBalanceConfigClick(player, event)
                return
            }

            if (category == ConfigCategory.DAMAGE) {
                if (event.rawSlot == 45) {
                    player.openConfigInventory()
                    return
                }
                if (!player.isOp) return
                val type = getDamageMultiplierTypeFromSlot(event.rawSlot) ?: return
                GameSettings.adjustDamageMultiplier(
                    type,
                    increase = event.isLeftClick,
                    stepMultiplier = if (event.isShiftClick) 10 else 1,
                )
                player.openConfigCategoryInventory(category)
                return
            }

            if (event.rawSlot == 18) {
                player.openConfigInventory()
                return
            }
            if (category == ConfigCategory.PERSONAL) {
                if (event.rawSlot != 13) return
                PlayerPreferenceManager.toggleDescriptionViewMode(player)
                findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
                    ?.find { it.uniqueId == player.uniqueId }
                    ?.let(::refreshClassItemDescriptions)
                player.openConfigCategoryInventory(category)
                return
            }
            if (!player.isOp) return
            val setting = configSetting(category, event.rawSlot) ?: return
            GameSettings.adjust(setting, event.isLeftClick, if (event.isShiftClick) 10 else 1)
            player.openConfigCategoryInventory(category)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)) {
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

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_CLASS_LIST_INVENTORY)) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val clickItem = event.currentItem ?: return
            classListHandler(player, clickItem, inventory)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_TRAINING_CLASS_LIST_INVENTORY)) {
            event.isCancelled = true
            if (event.rawSlot !in 0 until inventory.topInventory.size) return
            val clickItem = event.currentItem ?: return
            trainingClassListHandler(player, clickItem, inventory)
            return
        }

        if (PlayerTagManager.hasFlag(player, PlayerFlag.OPEN_CLASS_STATUS_INVENTORY)) {
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
                if (!player.isOp && gameClass.rank == Rank.SPECIAL) return
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                PlayerTagManager.setValue(player, PlayerTagValue.CLASS_LIST_PAGE, currentPage)
                PlayerTagManager.setValue(player, PlayerTagValue.CLASS_STATUS_RETURN, "list")
                PlayerTagManager.addFlag(player, PlayerFlag.OPENING_CLASS_STATUS_INVENTORY)
                player.openClassStatusInventory(gameClass)
                PlayerTagManager.addFlag(player, PlayerFlag.OPEN_CLASS_STATUS_INVENTORY)
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
                if (!player.isOp && gameClass.rank == Rank.SPECIAL) return
                player.closeInventory()
                PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_TRAINING_CLASS_LIST_INVENTORY)
                player.startTraining(gameClass)
                return
            }
        }
    }

    private fun getCurrentPageFromTitle(title: String): Int {
        val matchResult = pageRegex.find(title) ?: return 0
        return matchResult.groupValues[1].toInt() - 1
    }

    private fun configSetting(category: ConfigCategory, inventorySlot: Int): GameSetting? = when (category) {
        ConfigCategory.PERSONAL -> null
        ConfigCategory.GAME -> when (inventorySlot) {
            11 -> GameSetting.REFRESH_CHANCES
            13 -> GameSetting.COOLDOWN_FLOW_MULTIPLIER
            15 -> GameSetting.COUNTDOWN_SECONDS
            else -> null
        }

        ConfigCategory.RANK -> when (inventorySlot) {
            10 -> GameSetting.RANK_SPECIAL_WEIGHT
            11 -> GameSetting.RANK_L_WEIGHT
            12 -> GameSetting.RANK_S_WEIGHT
            14 -> GameSetting.RANK_A_WEIGHT
            15 -> GameSetting.RANK_B_WEIGHT
            16 -> GameSetting.RANK_C_WEIGHT
            else -> null
        }

        ConfigCategory.SCATTER -> when (inventorySlot) {
            10 -> GameSetting.SCATTER_MINIMUM_RADIUS
            13 -> GameSetting.SCATTER_MAXIMUM_RADIUS
            16 -> GameSetting.MINIMUM_PLAYER_DISTANCE
            else -> null
        }

        ConfigCategory.BORDER -> when (inventorySlot) {
            10 -> GameSetting.BORDER_ENABLED
            11 -> GameSetting.BORDER_INITIAL_SIZE
            12 -> GameSetting.BORDER_DELAY_SECONDS
            13 -> GameSetting.BORDER_SHRINK_SECONDS
            14 -> GameSetting.BORDER_MINIMUM_SIZE
            15 -> GameSetting.BORDER_CENTER_MINIMUM_DISTANCE
            16 -> GameSetting.BORDER_CENTER_MAXIMUM_DISTANCE
            22 -> GameSetting.FINAL_BORDER_DESCENT_SECONDS
            23 -> GameSetting.BORDER_DAMAGE_BUFFER
            24 -> GameSetting.BORDER_DAMAGE_PER_BLOCK
            25 -> GameSetting.FINAL_BORDER_DAMAGE
            26 -> GameSetting.FINAL_BORDER_DAMAGE_INTERVAL_SECONDS
            else -> null
        }

        ConfigCategory.COMBAT -> when (inventorySlot) {
            10 -> GameSetting.PLAYER_LIST_VISIBLE
            12 -> GameSetting.DEATH_MESSAGES_ENABLED
            14 -> GameSetting.DEATH_MESSAGES_SHOW_KILLER
            16 -> GameSetting.DEATH_MESSAGES_SHOW_CAUSE
            22 -> GameSetting.DAMAGE_INDICATORS_ENABLED
            else -> null
        }

        ConfigCategory.DAMAGE -> null
        ConfigCategory.CLASS_BALANCE -> null
    }

    private fun handleClassBalanceConfigClick(player: Player, event: InventoryClickEvent) {
        val selectedClass = getSelectedClassBalance(player)
        val page = getOpenClassBalancePage(player)
        if (selectedClass == null) {
            when (event.rawSlot) {
                45 -> player.openConfigInventory()
                48 -> player.openClassBalanceListInventory(page - 1)
                50 -> player.openClassBalanceListInventory(page + 1)
                else -> event.currentItem?.let(::getClassFromItem)?.let { gameClass ->
                    player.openClassBalanceDetailInventory(gameClass)
                }
            }
            return
        }

        when (event.rawSlot) {
            18 -> player.openClassBalanceListInventory(page)
            22 -> {
                ClassBalanceManager.reset(selectedClass)
                player.openClassBalanceDetailInventory(selectedClass)
            }

            else -> {
                val field = when (event.rawSlot) {
                    10 -> ClassBalanceField.DAMAGE
                    11 -> ClassBalanceField.HEALING
                    12 -> ClassBalanceField.RANGE
                    13 -> ClassBalanceField.OVERALL
                    14 -> ClassBalanceField.STATUS_DURATION
                    15 -> ClassBalanceField.STATUS_POWER
                    16 -> ClassBalanceField.COOLDOWN_FLOW
                    else -> null
                } ?: return
                ClassBalanceManager.adjust(
                    selectedClass,
                    field,
                    increase = event.isLeftClick,
                    stepMultiplier = if (event.isShiftClick) 10 else 1,
                )
                player.openClassBalanceDetailInventory(selectedClass)
            }
        }
    }
}
