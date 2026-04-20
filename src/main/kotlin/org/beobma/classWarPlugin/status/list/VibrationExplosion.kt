package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType

class VibrationExplosion : StatusAbnormality() {
    override val name: String
        get() = Keyword.VibrationExplosion.string
    override val description: List<String>
        get() = listOf(
            Keyword.VibrationExplosion.description!!,
            "",
            "<gray>수치 없음",
            "<gray>지속시간 없음",
            "<gray>효과 발동 후 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var duration: Int? = 1

    override fun updatePower(amount: Int) {
        val vibration = entityData.getStatus<Vibration>()

        if (vibration == null || vibration.power <= 0) {
            this.remove()
            return
        }
        entityData.damage(vibration.power.toDouble(), DamageType.StatusAbnormality, casterData)
        vibration.remove()
        this.remove()
    }
}