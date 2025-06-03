package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.manager.GameClassManager.toItemStack
import org.beobma.classWarPlugin.manager.GameManager.gameClassList
import org.beobma.classWarPlugin.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object InventoryManager {
    private val miniMessage = MiniMessage.miniMessage()
    private val nextPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(MiniMessage.miniMessage().deserialize("<gray>다음 페이지"))
        }
    }
    private val previousPage = ItemStack(Material.ARROW, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(MiniMessage.miniMessage().deserialize("<gray>이전 페이지"))
        }
    }
    private val nullItem = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS, 1).apply {
        itemMeta = itemMeta.apply {
            displayName(MiniMessage.miniMessage().deserialize("<gray>비어있음"))
        }
    }

    fun PlayerData.openClassPickInventory(page: Int) {
        val game = game
        val classList = game.classList
        val totalPages = (classList.size + 18 - 1) / 18
        if (page < 0 || page >= totalPages) return
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("클래스 목록 (페이지 ${page + 1}/${totalPages})"))
        val startIdx = page * 18
        val endIdx = minOf(startIdx + 18, classList.size)

        for (i in 0..26) {
            inventory.setItem(i, nullItem)
        }

        for (i in startIdx until endIdx) {
            val gameClass = classList[i] ?: return
            val classItemType = gameClass.classItemMaterial
            val name = miniMessage.deserialize(gameClass.name)
            val description = gameClass.description.map { miniMessage.deserialize(it) }
            val classItem = ItemStack(classItemType, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(name)
                    lore(description)
                }
            }
            inventory.setItem(i - startIdx, classItem)
        }

        if (page > 0) {
            inventory.setItem(18, previousPage)
        }

        if (page < totalPages - 1) {
            inventory.setItem(26, nextPage)
        }

        player.addScoreboardTag("openClassPickInventory")
        player.openInventory(inventory)
    }

    fun Player.openClassListInventory(page: Int) {
        val classList = gameClassList
        val totalPages = (classList.size + 18 - 1) / 18
        if (page < 0 || page >= totalPages) return
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize("클래스 목록 (페이지 ${page + 1}/${totalPages})"))
        val startIdx = page * 18
        val endIdx = minOf(startIdx + 18, classList.size)

        for (i in 0..26) {
            inventory.setItem(i, nullItem)
        }

        for (i in startIdx until endIdx) {
            val gameClass = classList[i]
            val classItemType = gameClass.classItemMaterial
            val name = miniMessage.deserialize(gameClass.name)
            val description = gameClass.description.map { miniMessage.deserialize(it) }
            val classItem = ItemStack(classItemType, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(name)
                    lore(description)
                }
            }
            inventory.setItem(i - startIdx, classItem)
        }

        if (page > 0) {
            inventory.setItem(18, previousPage)
        }

        if (page < totalPages - 1) {
            inventory.setItem(26, nextPage)
        }

        scoreboardTags.add("openClassListInventory")
        openInventory(inventory)
    }

    fun Player.openClassStatusInventory(gameClass: GameClass) {
        val inventory = Bukkit.createInventory(null, 27, miniMessage.deserialize(gameClass.name))
        inventory.setItem(0, gameClass.weapon.toItemStack())
        for (i in 0..gameClass.skills.size) {
            val skill = gameClass.skills.getOrNull(i) ?: break
            val material = when (i) {
                0 -> Material.RED_DYE
                1 -> Material.ORANGE_DYE
                2 -> Material.YELLOW_DYE
                3 -> Material.GREEN_DYE
                else -> Material.RED_DYE
            }
            val name = miniMessage.deserialize(skill.name)
            val description = skill.description.map { miniMessage.deserialize(it) }
            val itemStack = ItemStack(material, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(name)
                    lore(description)
                }
            }
            inventory.setItem(i + 1, itemStack)
        }
        for (i in gameClass.skills.size + 1..gameClass.passives.size + gameClass.skills.size + 1) {
            val passive = gameClass.passives.getOrNull(i - gameClass.skills.size - 1) ?: break
            val material = Material.WHITE_DYE
            val name = miniMessage.deserialize(passive.name)
            val description = passive.description.map { miniMessage.deserialize(it) }
            val itemStack = ItemStack(material, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(name)
                    lore(description)
                }
            }
            inventory.setItem(i, itemStack)
        }
        openInventory(inventory)
    }
}