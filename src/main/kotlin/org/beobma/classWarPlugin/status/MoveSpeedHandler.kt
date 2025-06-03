package org.beobma.classWarPlugin.status

import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.moveSpeedChanged

abstract class MoveSpeedHandler : StatusAbnormality() {
    override var maxPower: Int? = null
    override var duration: Int? = null
    override val canRemove: Boolean = true

    override fun onPowerChanged() {
        playerData.moveSpeedChanged()
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        playerData.moveSpeedChanged()
        super.onRemoveStatusAbnormality()
    }
}