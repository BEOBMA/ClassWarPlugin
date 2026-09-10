package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

abstract class AgentMechanicStatus(private val keyword: Keyword) : StatusAbnormality() {
    override val name = keyword.string
    override val description = listOf(keyword.requireDescription())
    override val canRemove = false
    override val isClassMechanic = true
    override var duration: Int? = null
    private var value = "<dark_gray>대기</dark_gray>"
    fun synchronize(text: String): Boolean {
        if (value == text) return false
        value = text
        return true
    }
    override fun actionBarText() = "${keyword.string}: $value"
}
class CaduceusStatus : AgentMechanicStatus(Keyword.Caduceus)
class DirectiveStatus : AgentMechanicStatus(Keyword.Directive)
// Retained for compatibility; damage multipliers are internal state, not HUD resources.
class AgentDamageDealtStatus : AgentMechanicStatus(Keyword.AgentDamageDealt) {
    override val showInActionBar = false
}
class AgentDamageTakenStatus : AgentMechanicStatus(Keyword.AgentDamageTaken) {
    override val showInActionBar = false
}
