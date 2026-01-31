package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Silence : StatusAbnormality() {
    override val name: String
        get() = Keyword.Silence.string
    override val description: List<String>
        get() = listOf(
            Keyword.Silence.description ?: ""
        )
    override val canRemove: Boolean = false
    override var maxPower: Int? = 1
    override var power: Int = 1
    override var duration: Int? = null

    override fun onDurationChanged() {
        entityStatus.canSkillUse = false
        super.onDurationChanged()
    }

    override fun onRemoveStatusAbnormality() {
        entityStatus.canSkillUse = true
        super.onRemoveStatusAbnormality()
    }
}