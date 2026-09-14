package org.beobma.classWarPlugin.gameClass.mechanics

/** One shared timeline for the cylinder particles, cartridge clicks and action bar. */
internal data class RevolverReloadFrame(val elapsed: Int, val loadedChambers: Int, val label: String) {
    companion object {
        fun at(remainingTicks: Int): RevolverReloadFrame {
            val elapsed = 40 - remainingTicks.coerceIn(0, 40)
            val loaded = ((elapsed - 8) / 4).coerceIn(0, 6)
            val label = when {
                elapsed < 8 -> "탄피 배출"
                elapsed < 34 -> "탄환 삽입"
                elapsed < 40 -> "실린더 잠금"
                else -> "장전 완료"
            }
            return RevolverReloadFrame(elapsed, loaded, label)
        }
    }
}
