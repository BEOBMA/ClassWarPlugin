package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.damage.DamageContext

interface WhenHitHandler {
    fun whenHit(context: DamageContext) {}

    fun whenAttackHit(context: DamageContext) {}

    fun whenSkillAttackHit(context: DamageContext) {}

    fun whenStatusEffectAttackHit(context: DamageContext) {}
}
