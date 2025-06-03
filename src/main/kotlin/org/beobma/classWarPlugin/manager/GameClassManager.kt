package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.gameClass.Weapon
import org.bukkit.inventory.ItemStack

object GameClassManager {
    private val miniMessage = MiniMessage.miniMessage()

    fun Weapon.toItemStack(): ItemStack {
        val itemStack = ItemStack(material, 1).apply {
            itemMeta.apply {
                displayName(miniMessage.deserialize(name))
                lore(description.map { miniMessage.deserialize(it) })
            }
        }
        return itemStack
    }
}