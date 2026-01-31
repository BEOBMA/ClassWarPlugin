package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class Abyss : StatusAbnormality() {
    override val name: String
        get() = Keyword.Abyss.string
    override val description: List<String>
        get() = listOf(
            Keyword.Abyss.description ?: ""
        )
    override val canRemove: Boolean = false
    override var maxPower: Int? = 1
    override var power: Int = 1
    override var duration: Int? = null

    override fun onDurationChanged() {
        val entity = entity
        if (entity is LivingEntity) {
            entity.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 1, 255, false, false, false))
            super.onDurationChanged()
        }
    }
}