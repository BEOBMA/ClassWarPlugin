package org.beobma.classWarPlugin.damage

/** Only a fully recharged direct melee hit is allowed to enter vanilla/plugin damage processing. */
internal object BasicAttackReadiness {
    fun ready(charge: Float): Boolean = charge.isFinite() && charge >= 1.0f
}
