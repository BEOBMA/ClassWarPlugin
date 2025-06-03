package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.status.StatusAbnormality

class Vibration : StatusAbnormality() {
    override val name: String
        get() = Keyword.Vibration.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Vibration] ?: ""
        )
    override val canRemove: Boolean = false
    override var maxPower: Int? = null
    override var duration: Int? = null
}