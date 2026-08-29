package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.damage.DamageContext

/** 피격자가 받은 커스텀 피해의 경로별 처리기다. */
interface WhenHitHandler {
    /** 모든 피해 경로에 공통으로 먼저 호출된다. */
    fun whenHit(context: DamageContext) {}

    /** 근접 또는 원거리 기본 공격 경로에서 호출된다. */
    fun whenAttackHit(context: DamageContext) {}

    /** 스킬 피해 경로에서 호출된다. */
    fun whenSkillAttackHit(context: DamageContext) {}

    /** 상태이상 피해 경로에서 호출된다. */
    fun whenStatusEffectAttackHit(context: DamageContext) {}
}
