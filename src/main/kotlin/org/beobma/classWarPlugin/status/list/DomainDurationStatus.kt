package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

/** The owning session supplies combat time; the status manager must not run a second countdown. */
class DomainDurationStatus : StatusAbnormality() {
    override val name = Keyword.Area.string
    override val description = listOf("<gray>현재 영역의 진행 단계와 남은 시간을 표시한다.")
    override val canRemove = false
    override val isClassMechanic = true
    override val showPower = false
    override val showMaxPower = false
    override var power = 1
    private var label = "전개 중"

    fun synchronize(introducing: Boolean, dissolving: Boolean, remainingTicks: Int): Boolean {
        val next = when {
            introducing -> "전개 중"
            dissolving -> "해제 중"
            else -> "${(remainingTicks.coerceAtLeast(0).toLong() + 19) / 20}초"
        }
        if (next == label) return false
        label = next
        return true
    }
    override fun actionBarText() = "$name <dark_red>$label</dark_red>"
}
