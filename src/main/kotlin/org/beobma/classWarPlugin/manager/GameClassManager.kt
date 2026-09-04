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

/** 클래스 무기 표시와 아이템의 소유 클래스 식별 정보를 변환한다. */
object GameClassManager {
    private val miniMessage = MiniMessage.miniMessage()
    private val weaponClassKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "weapon-class")

    /** 무기를 표시용 아이템으로 만든다. [viewer]가 있으면 개인 설명 표시 설정을 반영한다. */
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

    /** 클래스 무기 아이템을 만들고 원래 클래스의 정규 이름을 영속 데이터에 기록한다. */
    fun GameClass.toWeaponItemStack(viewer: Player? = null, template: ItemStack? = null): ItemStack =
        (template?.clone() ?: weapon.toItemStack(viewer)).apply {
        if (!type.isAir) {
            itemMeta = itemMeta.apply {
                persistentDataContainer.set(
                    weaponClassKey,
                    PersistentDataType.STRING,
                    this@toWeaponItemStack.classId,
                )
            }
        }
    }

    /** 무기 아이템에 기록된 클래스 정규 이름을 반환한다. */
    fun getWeaponClassId(item: ItemStack): String? = item.itemMeta
        ?.persistentDataContainer
        ?.get(weaponClassKey, PersistentDataType.STRING)
}
