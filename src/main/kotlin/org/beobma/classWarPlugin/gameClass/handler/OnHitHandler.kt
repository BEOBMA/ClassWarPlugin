package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.damage.DamageContext

interface OnHitHandler {
    fun onHit(context: DamageContext) {}

    fun onAttackHit(context: DamageContext) {}

    fun onSkillAttackHit(context: DamageContext) {}

    fun onStatusEffectAttackHit(context: DamageContext) {}
}
