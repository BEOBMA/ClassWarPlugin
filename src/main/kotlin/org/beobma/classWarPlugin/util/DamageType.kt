package org.beobma.classWarPlugin.util

/** 방어 요소 적용 여부를 결정하는 피해 계산 형식이다. */
enum class DamageType(val isFixed: Boolean) {
    /** 방어력, 저항과 흡수 체력을 순서대로 적용한다. */
    Normal(false),

    /** 모든 방어 요소를 우회하는 고정 피해다. */
    True(true),

    /** 상태이상이 발생시킨 고정 피해다. */
    StatusAbnormality(true)
}
