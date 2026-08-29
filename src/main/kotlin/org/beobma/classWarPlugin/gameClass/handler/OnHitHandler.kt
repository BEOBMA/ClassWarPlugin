package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.damage.DamageContext

/** 공격자가 가한 커스텀 피해의 경로별 처리기다. */
interface OnHitHandler {
    /** 모든 피해 경로에 공통으로 먼저 호출된다. */
    fun onHit(context: DamageContext) {}

    /** 근접 또는 원거리 기본 공격 경로에서 호출된다. */
    fun onAttackHit(context: DamageContext) {}

    /** 스킬 피해 경로에서 호출된다. */
    fun onSkillAttackHit(context: DamageContext) {}

    /** 상태이상 피해 경로에서 호출된다. */
    fun onStatusEffectAttackHit(context: DamageContext) {}
}
