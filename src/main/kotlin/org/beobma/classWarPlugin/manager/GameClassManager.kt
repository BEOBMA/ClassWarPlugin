package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object GameClassManager {
    private val miniMessage = MiniMessage.miniMessage()
    private val weaponClassKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "weapon-class")

    fun Weapon.toItemStack(viewer: Player? = null): ItemStack {
        if (material == Material.AIR) return ItemStack(Material.AIR)
        val itemStack = ItemStack(material, 1).apply {
            itemMeta = itemMeta.apply {
                displayName(miniMessage.deserialize(UtilManager.applyKeywords(name)))
            }
        }
        return if (viewer == null) {
            ItemDescriptionManager.apply(itemStack, description)
        } else {
            ItemDescriptionManager.applyForPlayer(itemStack, viewer, description, briefDescription)
        }
    }

    fun GameClass.toWeaponItemStack(viewer: Player? = null): ItemStack = weapon.toItemStack(viewer).apply {
        if (!type.isAir) {
            itemMeta = itemMeta.apply {
                persistentDataContainer.set(
                    weaponClassKey,
                    PersistentDataType.STRING,
                    this@toWeaponItemStack.javaClass.name,
                )
            }
        }
    }

    fun getWeaponClassId(item: ItemStack): String? = item.itemMeta
        ?.persistentDataContainer
        ?.get(weaponClassKey, PersistentDataType.STRING)
}
