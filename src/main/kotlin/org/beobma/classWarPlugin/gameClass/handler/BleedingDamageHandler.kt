package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.entity.EntityData

interface BleedingDamageHandler {
    fun onBleedingDamage(target: EntityData, power: Int)
}
