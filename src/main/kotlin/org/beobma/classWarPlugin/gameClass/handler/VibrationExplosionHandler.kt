package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.entity.EntityData

interface VibrationExplosionHandler {
    /** Called before damage; repeated explosions retain their initial power until all hits finish. */
    fun retainedVibrationPower(target: EntityData, power: Int): Int = 0
    fun onVibrationExplosion(target: EntityData)
}
