package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

/** Display projections of PioneerState. Combat ticks own expiry; no second status timer is started. */
abstract class PioneerResourceStatus(keyword: Keyword, maximum: Int) : StatusAbnormality() {
    override val name = keyword.string
    override val description = listOf(keyword.requireDescription())
    override val canRemove = false
    override val isClassMechanic = true
    override var maxPower: Int? = maximum
    override var duration: Int? = null
    private var remainingTenths: Long? = null
    private var initialized = false

    /** Updates without sending an intermediate action bar; the class batches all four resources. */
    fun synchronize(amount: Int, remainingTicks: Long? = null): Boolean {
        val nextPower = amount.coerceIn(0, maxPower ?: Int.MAX_VALUE)
        val nextTime = remainingTicks?.takeIf { nextPower > 0 }?.let { (it.coerceAtLeast(0) + 1) / 2 }
        val changed = !initialized || power != nextPower || remainingTenths != nextTime
        power = nextPower
        remainingTenths = nextTime
        initialized = true
        return changed
    }

    override fun actionBarText(): String {
        val color = if (power > 0) "white" else "dark_gray"
        val count = "<$color>$power</$color><dark_gray>/</dark_gray><gray>$maxPower</gray>"
        val time = remainingTenths?.let {
            " <dark_gray>|</dark_gray><yellow>${it / 10}.${it % 10}초</yellow>"
        } ?: ""
        return "$name: $count$time"
    }
}

class ForesightStatus : PioneerResourceStatus(Keyword.Foresight, 30)
class AccelerationStatus : PioneerResourceStatus(Keyword.Acceleration, 5)
class AccelerationBulletStatus : PioneerResourceStatus(Keyword.AccelerationBullet, 6)
class DisposalStatus : PioneerResourceStatus(Keyword.Disposal, 5)
