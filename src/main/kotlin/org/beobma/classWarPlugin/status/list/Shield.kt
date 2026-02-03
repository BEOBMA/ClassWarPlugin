package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Shield : StatusAbnormality() {
    override val name: String
        get() = Keyword.Shield.string
    override val description: List<String>
        get() = listOf(
            Keyword.Shield.description!!,
            "",
            "<gray>수치 개별 합산 적용",
            "<gray>지속시간 개별 적용",
            "<gray>지속시간 종료 시 개별 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = null
    override var duration: Int? = null
}