package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Vibration : StatusAbnormality() {
    override val name: String
        get() = Keyword.Vibration.string
    override val description: List<String>
        get() = listOf(
            Keyword.Vibration.description!!,
            "",
            "<gray>수치 합산 적용",
            "<gray>지속시간 연장 적용",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = null
    override var duration: Int? = null
}