package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class Stealth : StatusAbnormality() {
    override val name: String
        get() = Keyword.Stealth.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Stealth] ?: ""
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var duration: Int? = null

    override fun onDurationChanged() {
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 1, 0, false, false, true))
        super.onDurationChanged()
    }

    override fun onRemoveStatusAbnormality() {
        super.onRemoveStatusAbnormality()
    }
}