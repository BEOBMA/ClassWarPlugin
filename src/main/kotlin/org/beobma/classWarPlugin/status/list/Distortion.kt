package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

/** Presentation marker; the owning domain releases its timing and attribute leases together. */
class Distortion : StatusAbnormality() {
    override val name = Keyword.Distortion.string
    override val description = listOf(Keyword.Distortion.description ?: "")
    override val canRemove = false
    override val isHarmful = false // A domain introduction affects allies and enemies alike.
    override val growsWithStats = false
    override val showPower = false
    override val showMaxPower = false
    override var power = 1
}
