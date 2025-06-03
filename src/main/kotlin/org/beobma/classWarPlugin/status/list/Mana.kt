package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.dictionary
import org.beobma.classWarPlugin.status.StatusAbnormality

class Mana : StatusAbnormality() {
    override val name: String
        get() = Keyword.Mana.string
    override val description: List<String>
        get() = listOf(
            dictionary[Keyword.Mana] ?: "",
            "",
            "<dark_gray>최대치 100.",
            "<dark_gray>지속시간 없음.",
            "<dark_gray>사라지지 않음."
        )
    override val canRemove: Boolean = false
    override var maxPower: Int? = 100
    override var duration: Int? = null
}