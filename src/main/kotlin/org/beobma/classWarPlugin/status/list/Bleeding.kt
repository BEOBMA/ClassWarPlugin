package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusOnHitHandler
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.event.entity.EntityDamageByEntityEvent

class Bleeding : StatusAbnormality(), StatusOnHitHandler {
    override val name: String
        get() = Keyword.Bleeding.string
    override val description: List<String>
        get() = listOf(
            Keyword.Bleeding.description ?: "",
            "",
            "<dark_gray>최대치 없음."
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 100
    override var duration: Int? = null

    override fun onAttackHit(event: EntityDamageByEntityEvent, damagerData: PlayerData, entityData: PlayerData) {
        if (power <= 0) return
        entityData.damage(power.toDouble(), DamageType.StatusAbnormality, entityData)
        updatePower(power / 2)
    }
}
