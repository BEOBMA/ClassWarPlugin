package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.damage.DamageContext

/** 상태 보유자가 기본 공격에 피격된 뒤 호출되는 처리기다. */
interface StatusWhenHitHandler {
    fun whenAttackHit(context: DamageContext)
}
