package org.beobma.classWarPlugin.status

abstract class WhenDamageHandler : StatusAbnormality() {
    override val canRemove: Boolean = true
    override var maxPower: Int? = null
    override var duration: Int? = null
}