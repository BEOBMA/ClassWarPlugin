package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

/** 이동 자체가 아니라 위치나 속도를 크게 바꾸는 이동 스킬만 봉인한다. */
class Fix : StatusAbnormality() {
    override val name = Keyword.Fix.string
    override val description = listOf(Keyword.Fix.description ?: "")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null
}
