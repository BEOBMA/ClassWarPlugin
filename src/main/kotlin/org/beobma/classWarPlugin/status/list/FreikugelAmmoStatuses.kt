package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

abstract class FreikugelAmmoStatus(keyword: Keyword, maximum: Int) : StatusAbnormality() {
    override val name = keyword.string
    override val description = listOf(keyword.requireDescription())
    override val canRemove = false
    override val isClassMechanic = true
    override var maxPower: Int? = maximum
    override var duration: Int? = null
    private var reloadTicks = 0
    private var initialized = false

    fun synchronize(amount: Int, remainingReloadTicks: Int): Boolean {
        val count = amount.coerceIn(0, maxPower ?: 0)
        val remaining = remainingReloadTicks.coerceIn(0, 40)
        val changed = !initialized || power != count || reloadTicks != remaining
        power = count
        reloadTicks = remaining
        initialized = true
        return changed
    }

    override fun actionBarText(): String {
        if (reloadTicks > 0) {
            val frame = org.beobma.classWarPlugin.gameClass.mechanics.RevolverReloadFrame.at(reloadTicks)
            val filled = (40 - reloadTicks) / 4
            val tenths = (reloadTicks + 1) / 2
            val cylinder = if (maxPower == 6) chamberText(frame.loadedChambers) + " <gray>${frame.label}</gray> " else ""
            return "$name: $cylinder<yellow>재장전</yellow> <gold>${"▰".repeat(filled)}</gold>" +
                "<dark_gray>${"▱".repeat(10 - filled)}</dark_gray> <yellow>${tenths / 10}.${tenths % 10}초</yellow>"
        }
        val color = if (power > 0) "gold" else "dark_gray"
        val cylinder = if (maxPower == 6) chamberText(power) + " " else ""
        return "$name: $cylinder<$color>$power</$color><dark_gray>/</dark_gray><gray>$maxPower</gray>"
    }

    private fun chamberText(loaded: Int): String = "<gray>⟦</gray><gold>${"●".repeat(loaded.coerceIn(0, 6))}</gold>" +
        "<dark_gray>${"○".repeat(6 - loaded.coerceIn(0, 6))}</dark_gray><gray>⟧</gray>"
}

class RevolverBulletStatus : FreikugelAmmoStatus(Keyword.Bullet, 6)
class FreikugelBulletStatus : FreikugelAmmoStatus(Keyword.FreikugelBullet, 1)
