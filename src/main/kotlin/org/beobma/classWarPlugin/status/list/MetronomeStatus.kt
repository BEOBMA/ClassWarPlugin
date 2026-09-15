package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class MetronomeStatus : StatusAbnormality() {
    override val name = Keyword.VariableRhythm.string
    override val description = listOf(Keyword.VariableRhythm.requireDescription())
    override val canRemove = false
    override val isClassMechanic = true
    private var text = "<gray>기준 박자 60 BPM"
    fun synchronize(streak: Int, division: Int, title: String, bpm: Int, phase: Int, feedback: String) {
        power = 1
        val beat = (0..7).joinToString("") { if (it == phase) "<gold>◆</gold>" else "<dark_gray>·</dark_gray>" }
        text = "<white>$title</white> <aqua>$bpm BPM</aqua> <gray>1/$division</gray> " +
            "<yellow>${streak}연속</yellow> $beat $feedback"
    }
    override fun actionBarText() = "$name $text"
}
