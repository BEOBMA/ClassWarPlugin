package org.beobma.classWarPlugin.status

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.event.entity.EntityDamageByEntityEvent

interface StatusOnHitHandler {
    fun onAttackHit(event: EntityDamageByEntityEvent, damagerData: PlayerData, entityData: PlayerData)
}
