package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.entity.EntityData

/** 출혈 틱 피해가 적용되기 전에 클래스가 값을 조정할 수 있는 처리기다. */
interface BleedingDamageHandler {
    /** [target]에게 세기 [power]의 출혈 피해가 발생할 때 호출된다. */
    fun onBleedingDamage(target: EntityData, power: Int)
}
