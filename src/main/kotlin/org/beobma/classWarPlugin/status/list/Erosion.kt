package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Erosion : StatusAbnormality() {
    override val name = Keyword.Erosion.string
    override val description = listOf(Keyword.Erosion.description ?: "")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override var duration: Int? = 8
    override val showPower = false
    override val showMaxPower = false
}
