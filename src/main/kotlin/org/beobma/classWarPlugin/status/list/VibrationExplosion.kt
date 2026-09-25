package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType

class VibrationExplosion(private val repetitions: Int = 1) : StatusAbnormality() {
    override val name: String
        get() = Keyword.VibrationExplosion.string
    override val description: List<String>
        get() = listOf(
            Keyword.VibrationExplosion.requireDescription(),
            "",
            "<gray>수치 없음",
            "<gray>지속시간 없음",
            "<gray>효과 발동 후 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var duration: Int? = 1

    override fun onPowerChanged() {
        val vibration = entityData.getStatus<Vibration>()

        if (vibration == null || vibration.power <= 0) {
            this.remove()
            return
        }
        val power = vibration.power
        repeat(repetitions.coerceIn(1, 2)) {
            entityData.damage(power * 0.5, DamageType.StatusAbnormality, casterData,
                appearance = org.beobma.classWarPlugin.damage.DamageAppearance.VIBRATION)
        }
        vibration.remove()
        org.beobma.classWarPlugin.ability.AbilityTree.handlers(casterData.gameClasses,
            org.beobma.classWarPlugin.gameClass.handler.VibrationExplosionHandler::class.java).forEach { bound ->
            bound.call { it.onVibrationExplosion(entityData) }
        }
        this.remove()
    }
}
