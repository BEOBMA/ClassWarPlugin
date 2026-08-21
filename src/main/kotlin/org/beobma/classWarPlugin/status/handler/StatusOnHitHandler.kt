package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.damage.DamageContext

interface StatusOnHitHandler {
    fun onAttackHit(context: DamageContext)
}
