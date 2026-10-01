package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

class Mana : StatusAbnormality() {
    private val infiniteDisplays = mutableSetOf<Any>()
    /** A scoped presentation lease; does not corrupt the shared numeric resource. */
    fun displayInfinite(owner: Any): AutoCloseable {
        infiniteDisplays += owner
        return AutoCloseable { infiniteDisplays -= owner }
    }
    override fun actionBarText(): String = if (infiniteDisplays.isNotEmpty()) "$name: <light_purple>∞</light_purple>" else super.actionBarText()
    override val name: String = Keyword.Mana.string
    override val description: List<String> = listOf(
        Keyword.Mana.description ?: "",
        "",
        "<dark_gray>수치 합산 적용 (최대 수치 100)",
        "<dark_gray>지속시간 없음.",
        "<dark_gray>사라지지 않음."
    )
    override val canRemove: Boolean = false
    override val isClassMechanic: Boolean = true
    override val growsWithStats = true
    override var maxPower: Int? = 100
    override var duration: Int? = null
}
