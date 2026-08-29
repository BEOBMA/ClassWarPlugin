package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Silence : StatusAbnormality() {
    override val name: String
        get() = Keyword.Silence.string
    override val description: List<String>
        get() = listOf(
            Keyword.Silence.requireDescription(),
            "",
            "<gray>수치 없음",
            "<gray>지속시간 연장 적용",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var power: Int = 1
    override val showMaxPower: Boolean = false
    override val showPower: Boolean = false
    override var duration: Int? = null

}
