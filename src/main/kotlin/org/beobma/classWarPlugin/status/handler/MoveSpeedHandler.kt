package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.moveSpeedChanged
import org.beobma.classWarPlugin.status.StatusAbnormality

/** 세기 변경과 제거 시 엔티티의 이동 속성 값을 다시 계산하는 상태 기반 클래스다. */
abstract class MoveSpeedHandler : StatusAbnormality() {
    override var maxPower: Int? = null
    override var duration: Int? = null
    override val canRemove: Boolean = true

    override fun onPowerChanged() {
        entityData.moveSpeedChanged()
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        entityData.moveSpeedChanged()
        super.onRemoveStatusAbnormality()
    }
}
