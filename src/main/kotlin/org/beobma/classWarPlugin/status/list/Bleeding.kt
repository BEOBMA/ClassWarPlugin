package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.status.StatusAbnormality

class Bleeding : StatusAbnormality() {
    override val name: String
        get() = Keyword.Bleeding.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Bleeding] ?: "",
            "",
            "<dark_gray>최대치 없음."
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 100
    override var duration: Int? = null
}