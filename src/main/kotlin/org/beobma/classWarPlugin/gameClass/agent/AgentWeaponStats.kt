package org.beobma.classWarPlugin.gameClass.agent

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.ItemMeta

internal object AgentWeaponStats {
    /** Visual forms keep their speed modifiers, but all receive the iron sword's attack damage. */
    fun applyAppearance(meta: ItemMeta, appearance: Material) {
        meta.itemModel = appearance.key
        meta.isUnbreakable = true
        meta.attributeModifiers = ironSwordDamageModifiers(
            appearance.getDefaultAttributeModifiers(EquipmentSlot.HAND),
            Material.IRON_SWORD.getDefaultAttributeModifiers(EquipmentSlot.HAND),
            Attribute.ATTACK_DAMAGE,
        )
    }

    internal fun <K : Any, V : Any> ironSwordDamageModifiers(
        appearance: Multimap<K, V>, ironSword: Multimap<K, V>, damageKey: K,
    ): Multimap<K, V> = ArrayListMultimap.create(appearance).apply {
        removeAll(damageKey)
        putAll(damageKey, ironSword.get(damageKey))
    }

    /** Swords subtract 2.4 from base attack speed; multiplying the base directly can make greatswords unable to attack. */
    fun swordSpeedMultiplier(base: Double, multiplier: Double): Double {
        if (base <= 0.0 || multiplier == 1.0) return 1.0
        return (2.4 + (base - 2.4).coerceAtLeast(.1) * multiplier) / base
    }
}
