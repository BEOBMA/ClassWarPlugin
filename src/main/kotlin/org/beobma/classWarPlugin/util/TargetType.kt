package org.beobma.classWarPlugin.util

/** 범위·광선·투사체가 선택할 수 있는 관계 기반 대상 범주다. */
enum class TargetType {
    /** 효과 생성자 자신만 포함한다. */
    Self,

    /** 현재 경기 규칙상 효과 생성자의 적만 포함한다. */
    Enemy,

    /** 관계와 관계없이 모든 유효 대상을 포함한다. */
    All,
}
