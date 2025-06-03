package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class Silence : StatusAbnormality() {
    override val name: String
        get() = Keyword.Silence.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Silence] ?: ""
        )
    override val canRemove: Boolean = false
    override var maxPower: Int? = 1
    override var power: Int = 1
    override var duration: Int? = null

    override fun onDurationChanged() {
        playerStatus.canSkillUse = false
        super.onDurationChanged()
    }

    override fun onRemoveStatusAbnormality() {
        playerStatus.canSkillUse = true
        super.onRemoveStatusAbnormality()
    }
}