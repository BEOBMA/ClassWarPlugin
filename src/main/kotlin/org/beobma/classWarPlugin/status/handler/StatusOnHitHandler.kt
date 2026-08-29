package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.damage.DamageContext

/** 상태 보유자가 기본 공격을 적중시킨 뒤 호출되는 처리기다. */
interface StatusOnHitHandler {
    fun onAttackHit(context: DamageContext)
}
