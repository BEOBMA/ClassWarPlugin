package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Invincibility : StatusAbnormality() {
    override val name = Keyword.Invincibility.string
    override val description = listOf(Keyword.Invincibility.description ?: "")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null
}
