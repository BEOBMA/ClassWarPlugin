package org.beobma.classWarPlugin.gameClass.handler

import org.bukkit.event.entity.EntityDamageEvent

interface EnvironmentalDamageHandler {
    fun onEnvironmentalDamage(event: EntityDamageEvent)
}
