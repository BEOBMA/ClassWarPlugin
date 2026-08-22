package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.status.StatusAbnormality

class BleedingLock : StatusAbnormality() {
    override val name = "<dark_red><bold>혈사병</bold><gray>"
    override val description = listOf("<gray>범위 안에 있는 동안 출혈 수치가 감소하지 않는다.")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null
}
