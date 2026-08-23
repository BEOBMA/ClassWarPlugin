package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.gameClass.handler.BleedingDamageHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusDurationMode
import org.beobma.classWarPlugin.status.handler.StatusOnHitHandler
import org.beobma.classWarPlugin.util.DamageType

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

    override fun onAttackHit(context: DamageContext) {
        if (power <= 0) return
        context.attacker.damage(power.toDouble(), DamageType.StatusAbnormality, casterData)
        casterData.gameClass?.passives?.filterIsInstance<BleedingDamageHandler>()
            ?.forEach { it.onBleedingDamage(context.attacker, power) }
        if (entityData.hasStatus<BleedingLock>()) return
        if (power / 2 <= 0) {
            this.remove()
            return
        }
        updatePower(power / 2)
    }
}
