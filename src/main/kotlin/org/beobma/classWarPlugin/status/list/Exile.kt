package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusDurationMode

class Exile : StatusAbnormality() {
    override val name: String
        get() = Keyword.Exile.string
    override val description: List<String>
        get() = listOf(
            Keyword.Exile.description!!,
            "",
            "<gray>수치 없음",
            "<gray>지속시간 연장 적용",
            "<gray>지속시간 종료 시 소멸"
        )

    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var power: Int = 1
    override var duration: Int? = null
    override var durationMode: StatusDurationMode = StatusDurationMode.Extend

    private val location = entity.location.clone()

    override fun onDurationChanged() {
        // 텔레포트 로직
        super.onDurationChanged()
    }

    override fun onRemoveStatusAbnormality() {
        entity.teleport(location)
        super.onRemoveStatusAbnormality()
    }
}