package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Shield : StatusAbnormality() {
    override val name: String
        get() = Keyword.Shield.string
    override val description: List<String>
        get() = listOf(
            Keyword.Shield.description ?: "",
            "",
            "<dark_gray>최대치 없음."
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = null
    override var duration: Int? = null
}