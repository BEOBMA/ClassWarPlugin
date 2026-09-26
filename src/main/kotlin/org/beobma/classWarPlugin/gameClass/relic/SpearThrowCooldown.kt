package org.beobma.classWarPlugin.gameClass.relic

/** Independent of spear pickup/recall state; advances only while the ability runs. */
internal class SpearThrowCooldown {
    companion object { const val SECONDS = 2 }
    private var remainingTicks = 0
    val ready get() = remainingTicks == 0
    fun onThrown() { remainingTicks = SECONDS * 20 }
    fun tick() { if (remainingTicks > 0) remainingTicks-- }
}
