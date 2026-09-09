package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.damage.DamageContext

/** Positive damage that survived evasion, immunity and shields (including training hits). */
interface ConfirmedHitHandler {
    fun onConfirmedHit(context: DamageContext) {}
    fun onConfirmedDamageTaken(context: DamageContext) {}
}
