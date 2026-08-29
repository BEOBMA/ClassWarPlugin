package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.status.StatusAbnormality

class Brightness : StatusAbnormality() {
    override val name = Keyword.Brightness.string
    override val description = listOf(Keyword.Brightness.description ?: "")
    override val canRemove = true
    override var power = 0
    override var maxPower: Int? = 5
    override var duration: Int? = null

    override fun onPowerChanged() {
        if (power >= 5) {
            entityData.addStatus(Snare(), casterData).applyStatus(duration = 2, powerSet = 1)
            remove()
            return
        }
        super.onPowerChanged()
    }
}
