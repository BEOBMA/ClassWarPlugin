package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusDurationMode
import org.beobma.classWarPlugin.status.StatusOnHitHandler
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.event.entity.EntityDamageByEntityEvent

class Bleeding : StatusAbnormality(), StatusOnHitHandler {
    override val name: String
        get() = Keyword.Bleeding.string
    override val description: List<String>
        get() = listOf(
            Keyword.Bleeding.description ?: "",
            "",
            "<gray>수치 합산 적용",
            "<gray>지속시간 연장 적용",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = null
    override var duration: Int? = null
    override var durationMode: StatusDurationMode = StatusDurationMode.Extend

    override fun onAttackHit(event: EntityDamageByEntityEvent, damagerData: PlayerData, entityData: PlayerData) {
        if (power <= 0) return
        damagerData.damage(power.toDouble(), DamageType.StatusAbnormality, damagerData)
        if (power / 2 <= 0) {
            this.remove()
            return
        }
        updatePower(power / 2)
    }
}
