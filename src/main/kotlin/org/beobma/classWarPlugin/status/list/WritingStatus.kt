package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class WritingStatus : StatusAbnormality() {
    override val name = Keyword.Writing.string
    override val description = listOf(Keyword.Writing.requireDescription())
    override val canRemove = false
    override val isClassMechanic = true
    override var duration: Int? = null
    private var correct = 0L
    private var incorrect = 0L
    private var line = 1
    private var total = 1

    fun synchronize(correct: Long, incorrect: Long, line: Int, total: Int) {
        this.correct = correct
        this.incorrect = incorrect
        this.line = line
        this.total = total
        power = 1
    }

    override fun actionBarText() = "$name <white>$line/$total</white> <green>정답 $correct</green> <red>오답 $incorrect</red>"
}
