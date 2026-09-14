package org.beobma.classWarPlugin.gameClass.writer

/** Integer score avoids floating-point drift: correct +1, incorrect -2. */
internal class WritingProgress {
    var correct = 0L
        private set
    var incorrect = 0L
        private set
    val score: Long get() = correct - 2L * incorrect
    val basicDamageBonus: Double get() = score / 10.0
    val damageTakenMultiplier: Double get() = (1.0 - score / 100.0).coerceAtLeast(0.0)

    fun submit(expected: String, input: String): Boolean {
        // Spacing and punctuation must match the displayed line.
        val matches = expected == input
        if (matches) correct++ else incorrect++
        return matches
    }
}
