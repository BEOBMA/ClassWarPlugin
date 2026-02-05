package org.beobma.classWarPlugin.status.handler

import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.attackSpeedChanged
import org.beobma.classWarPlugin.status.StatusAbnormality

abstract class AttackSpeedHandler : StatusAbnormality() {
    override var maxPower: Int? = null
    override var duration: Int? = null
    override val canRemove: Boolean = true

    override fun onPowerChanged() {
        entityData.attackSpeedChanged()
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        entityData.attackSpeedChanged()
        super.onRemoveStatusAbnormality()
    }
}