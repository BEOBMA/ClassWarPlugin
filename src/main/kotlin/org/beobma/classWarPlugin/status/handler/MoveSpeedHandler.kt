package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.moveSpeedChanged
import org.beobma.classWarPlugin.status.StatusAbnormality

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