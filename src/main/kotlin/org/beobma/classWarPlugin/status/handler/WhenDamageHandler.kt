package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.status.StatusAbnormality

/** 받는 피해 증감 효과가 공통으로 상속하는 상태 기반 클래스다. */
abstract class WhenDamageHandler : StatusAbnormality() {
    override val canRemove: Boolean = true
    override var maxPower: Int? = null
    override var duration: Int? = null
}
