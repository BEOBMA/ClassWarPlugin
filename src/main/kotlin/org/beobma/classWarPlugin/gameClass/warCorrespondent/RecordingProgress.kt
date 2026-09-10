package org.beobma.classWarPlugin.gameClass.warCorrespondent

/** Consecutive exposure per subject; leaving the frame discards that subject's progress. */
internal class RecordingProgress<P, D>(private val enhanced: Boolean = false) {
    enum class Result { RECORDING, COMPLETED, EXPIRED }
    private val players = mutableMapOf<P, Int>()
    private val deaths = mutableMapOf<D, Int>()
    var elapsed = 0
        private set
    var result = Result.RECORDING
        private set

    fun advance(visiblePlayers: Set<P>, visibleDeaths: Set<D>): Result {
        if (result != Result.RECORDING) return result
        // Once broadcasting, the skill always runs for the full four seconds.
        if (enhanced) {
            elapsed++
            if (elapsed >= 80) result = Result.COMPLETED
            return result
        }
        players.keys.retainAll(visiblePlayers)
        deaths.keys.retainAll(visibleDeaths)
        visiblePlayers.forEach { players[it] = ((players[it] ?: 0) + 1).coerceAtMost(20) }
        visibleDeaths.forEach { deaths[it] = ((deaths[it] ?: 0) + 1).coerceAtMost(40) }
        elapsed++
        result = when {
            players.values.count { it >= 20 } >= 2 || deaths.values.any { it >= 40 } -> Result.COMPLETED
            elapsed >= 80 -> Result.EXPIRED
            else -> Result.RECORDING
        }
        return result
    }

    fun playerProgress(id: P): Double = (players[id] ?: 0) / 20.0
    fun deathProgress(id: D): Double = (deaths[id] ?: 0) / 40.0
}
