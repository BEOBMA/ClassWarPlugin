package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.LivingEntity
import java.util.UUID

class Burn : StatusAbnormality() {
    private val durationPauseSources = mutableSetOf<UUID>()
    override val name = Keyword.Burn.string
    override val description = listOf("<gray>지속 시간 동안 불타며 화염 피해를 입는다.")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null

    override fun onDurationChanged() {
        val living = entity as? LivingEntity
        duration?.let { seconds -> living?.fireTicks = maxOf(living.fireTicks, seconds * 20) }
        super.onDurationChanged()
    }

    fun pauseDuration(sourceId: UUID) {
        durationPauseSources += sourceId
    }

    fun resumeDuration(sourceId: UUID) {
        durationPauseSources -= sourceId
    }

    override fun isDurationPaused(): Boolean = durationPauseSources.isNotEmpty()

    override fun onRemoveStatusAbnormality() {
        durationPauseSources.clear()
        super.onRemoveStatusAbnormality()
    }
}
