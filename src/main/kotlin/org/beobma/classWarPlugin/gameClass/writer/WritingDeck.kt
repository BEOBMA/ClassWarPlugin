package org.beobma.classWarPlugin.gameClass.writer

import kotlin.random.Random

/** Shuffle works from the very first selection; preserve the lines within each work. */
internal class WritingDeck(
    private val works: List<WritingWork> = WritingLibrary.works,
    private val random: Random = Random.Default,
) {
    private val remaining = ArrayDeque<WritingWork>()
    private var previous: WritingWork? = null

    init { require(works.isNotEmpty()) }

    fun reset() {
        remaining.clear()
        previous = null
    }

    fun next(): WritingWork {
        if (remaining.isEmpty()) {
            val shuffled = works.shuffled(random).toMutableList()
            if (shuffled.size > 1 && shuffled.first() == previous) {
                val replacement = random.nextInt(1, shuffled.size)
                val first = shuffled[0]
                shuffled[0] = shuffled[replacement]
                shuffled[replacement] = first
            }
            remaining.addAll(shuffled)
        }
        return remaining.removeFirst().also { previous = it }
    }
}
