package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class Stealth : StatusAbnormality() {
    override val name: String
        get() = Keyword.Stealth.string
    override val description: List<String>
        get() = listOf(
            Keyword.Stealth.description ?: ""
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var duration: Int? = null

    override val showMaxPower = false
    override val showPower = false

    override fun onDurationChanged() {
        val entity = entity
        if (entity is LivingEntity) {
            val effectDurationSeconds = duration ?: 1
            val effectDurationTicks = (effectDurationSeconds * 20).coerceAtLeast(1)
            entity.addPotionEffect(
                PotionEffect(PotionEffectType.INVISIBILITY, effectDurationTicks, 0, false, false, true)
            )
            super.onDurationChanged()
        }
    }

    override fun onRemoveStatusAbnormality() {
        val entity = entity
        if (entity is LivingEntity) {
            entity.removePotionEffect(PotionEffectType.INVISIBILITY)
        }
        super.onRemoveStatusAbnormality()
    }
}