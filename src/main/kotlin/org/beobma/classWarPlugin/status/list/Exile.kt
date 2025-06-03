package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

class Exile : StatusAbnormality() {
    override val name: String
        get() = Keyword.Exile.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Exile] ?: ""
        )

    override val canRemove: Boolean = false
    override var maxPower: Int? = 1
    override var power: Int = 1
    override var duration: Int? = null
    private val location = player.location.clone()

    override fun onDurationChanged() {
        // 텔레포트 로직
        super.onDurationChanged()
    }

    override fun onRemoveStatusAbnormality() {
        player.teleport(location)
        super.onRemoveStatusAbnormality()
    }
}