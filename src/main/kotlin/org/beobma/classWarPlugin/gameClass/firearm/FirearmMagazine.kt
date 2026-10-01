package org.beobma.classWarPlugin.gameClass.firearm

/** Also implemented by legacy firearms so the master can borrow their actual abilities. */
interface BorrowableFirearm {
    var reloadDisabled: Boolean
    var onMagazineEmpty: (() -> Unit)?
    val ammunition: Int
    val reloadSkillIds: Set<String>
}

enum class FirearmProfile(val capacity: Int, val cost: Int, val interval: Int, val pellets: Int,
    val damage: Double, val spread: Double, val range: Double, val recoil: Float,
    val reloadTicks: Int, val reloadSlow: Double, val automatic: Boolean) {
    SHOTGUN(4, 2, 14, 20, 1.0, 0.24, 24.0, 12f, 80, 0.60, false),
    ASSAULT(30, 1, 2, 1, 0.2, 0.025, 48.0, 1.4f, 60, 0.30, true),
    SMG(20, 1, 2, 1, 0.2, 0.035, 36.0, 0.65f, 40, 0.0, true),
    MINIGUN(300, 1, 1, 1, 0.1, 0.08, 48.0, 0.4f, 200, 0.90, true),
    ;
    fun damageForHits(hits: Int) = damage * hits.coerceIn(0, if (this == SHOTGUN) 8 else 1)
}

/** Uses owner-local combat ticks; clicks and held fire share the same cadence. */
class FirearmMagazine(val profile: FirearmProfile) {
    var bullets = profile.capacity; private set
    var remainingReload = 0; private set
    var totalReload = 0; private set
    private var nextShot = 0L
    val reloading get() = remainingReload > 0
    fun shoot(tick: Long): Boolean {
        if (reloading || bullets < profile.cost || tick < nextShot) return false
        bullets -= profile.cost
        nextShot = tick + profile.interval
        return true
    }
    fun reload(ticks: Int = profile.reloadTicks): Boolean {
        if (reloading) return false
        bullets = 0
        totalReload = ticks.coerceAtLeast(1)
        remainingReload = totalReload
        return true
    }
    fun tick(): Boolean {
        if (!reloading || --remainingReload > 0) return false
        bullets = profile.capacity
        return true
    }
}
