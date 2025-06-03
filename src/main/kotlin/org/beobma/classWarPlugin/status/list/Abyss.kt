package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class Abyss : StatusAbnormality() {
    override val name: String
        get() = Keyword.Abyss.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Abyss] ?: ""
        )
    override val canRemove: Boolean = false
    override var maxPower: Int? = 1
    override var power: Int = 1
    override var duration: Int? = null

    override fun onDurationChanged() {
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 1, 255, false, false, false))
        super.onDurationChanged()
    }
}