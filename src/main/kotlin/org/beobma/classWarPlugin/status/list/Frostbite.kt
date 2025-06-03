package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.status.StatusAbnormality

class Frostbite : StatusAbnormality() {
    override val name: String
        get() = Keyword.Frostbite.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Frostbite] ?: ""
        )
    override val canRemove: Boolean = false
    override var maxPower: Int? = 10
    override var duration: Int? = 5
    private var isOn = true

    override fun onPowerChanged() {
        val moveSpeedDecrease = playerData.addStatus(MoveSpeedDecrease())
        moveSpeedDecrease.updatePower(power * 5)
        moveSpeedDecrease.setContinueWhileIf { isOn }
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        isOn = false
        super.onRemoveStatusAbnormality()
    }
}