package org.beobma.classWarPlugin.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.GameManager.classPick
import org.beobma.classWarPlugin.manager.GameManager.gameClassList
import org.beobma.classWarPlugin.manager.GameManager.ready
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openClassPickInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openClassStatusInventory
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

class OnInventoryClickEvent : Listener {
    private val nextPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(MiniMessage.miniMessage().deserialize("<gray>다음 페이지"))
        }
    }.itemMeta
    private val previousPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(MiniMessage.miniMessage().deserialize("<gray>이전 페이지"))
        }
    }.itemMeta
    private val nullItem = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(MiniMessage.miniMessage().deserialize("<gray>비어있음"))
        }
    }.itemMeta

    @EventHandler
    fun onClickItem(event: InventoryClickEvent) {
        val player = event.whoClicked
        val inventory = event.view
        val clickItem = event.currentItem ?: return

        if (player !is Player) return

        if (player.scoreboardTags.contains("openClassPickInventory")) {
            val game = game ?: return
            classPickHandler(player, game, clickItem, inventory)
            event.isCancelled = true
            return
        }

        if (player.scoreboardTags.contains("openClassListInventory")) {
            classListHandler(player, clickItem, inventory)
            event.isCancelled = true
            return
        }

        event.isCancelled = true
        return
    }

    private fun classPickHandler(player: Player, game: Game, clickItem: ItemStack, inventory: InventoryView) {
        val itemMeta = clickItem.itemMeta ?: return
        val playerData = game.playerDatas.find { it.player == player } ?: return
        when (itemMeta) {
            previousPage -> {
                player.closeInventory()
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                playerData.openClassPickInventory(currentPage - 1)
                return
            }

            nextPage -> {
                player.closeInventory()
                val currentPage = getCurrentPageFromTitle(inventory.title().toString())
                playerData.openClassPickInventory(currentPage + 1)
                return
            }

            nullItem -> return

            else -> {
                val gameClass = game.classList.find { it?.classItemMaterial == clickItem.type } ?: return
                val index = game.classList.indexOf(gameClass)
                gameClass.inject(playerData)
                gameClass.skills.forEach { skill ->
                    skill.inject(playerData)
                }
                gameClass.passives.forEach { passive ->
                    passive.inject(playerData)
                }
                playerData.gameClass = gameClass
                game.classList[index] = null
                player.closeInventory()
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_GUITAR, 1.0F, 2.0F)
                player.removeScoreboardTag("openClassPickInventory")
                player.playerListName(
                    player.playerListName().append(MiniMessage.miniMessage().deserialize(" [ ${gameClass.name} ]"))
                )
                game.classPickOrder.remove(playerData)
                val pickPlayer = game.classPickOrder.firstOrNull() ?: run {
                    game.ready()
                    return
                }
                pickPlayer.classPick()
                return
            }
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
                val gameClassList = gameClassList
                val gameClass = gameClassList.find { it.classItemMaterial == clickItem.type } ?: return
                player.closeInventory()
                player.openClassStatusInventory(gameClass)
                return
            }
        }
    }

    private fun getCurrentPageFromTitle(title: String): Int {
        val regex = "페이지 (\\d+)/(\\d+)".toRegex()
        val matchResult = regex.find(title) ?: return 0
        return matchResult.groupValues[1].toInt() - 1
    }
}