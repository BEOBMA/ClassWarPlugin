package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.attackSpeedChanged
import org.beobma.classWarPlugin.status.StatusAbnormality

class AttackSpeedIncrease : StatusAbnormality() {
    override val name: String
        get() = "<green><bold>공격 속도 증가<gray>"
    override val description: List<String>
        get() = listOf(
            "<gray>공격 속도가 수치에 따라 증가한다.",
            "",
            "<dark_gray>최대치 없음."
        )
    override var maxPower: Int? = null
    override var duration: Int? = null
    override val canRemove: Boolean = true

    override fun onPowerChanged() {
        playerData.attackSpeedChanged()
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        playerData.attackSpeedChanged()
        super.onRemoveStatusAbnormality()
    }
}