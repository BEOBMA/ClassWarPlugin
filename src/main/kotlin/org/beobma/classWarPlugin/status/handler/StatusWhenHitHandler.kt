package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.damage.DamageContext

interface StatusWhenHitHandler {
    fun whenAttackHit(context: DamageContext)
}
