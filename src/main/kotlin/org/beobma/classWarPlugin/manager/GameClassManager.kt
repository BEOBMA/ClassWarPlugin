package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.gameClass.Weapon
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object GameClassManager {
    private val miniMessage = MiniMessage.miniMessage()

    fun Weapon.toItemStack(): ItemStack {
        if (material == Material.AIR) return ItemStack(Material.AIR)
        val itemStack = ItemStack(material, 1).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize(UtilManager.applyKeywords(name)))
            }
        }
        return ItemDescriptionManager.apply(itemStack, description)
    }
}
