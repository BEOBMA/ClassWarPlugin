package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.attribute.Attribute


enum class StatusDurationMode {
    Refresh,
    Extend
}

fun StatusAbnormality.applyStatus(
    duration: Int? = null,
    durationMode: StatusDurationMode = StatusDurationMode.Refresh,
    powerDelta: Int? = null,
    powerSet: Int? = null
) {
    powerSet?.let { updatePower(it) }
    powerDelta?.let { increasePower(it) }
    if (duration != null) {
        when (durationMode) {
            StatusDurationMode.Refresh -> updateDuration(duration)
            StatusDurationMode.Extend -> increaseDuration(duration)
        }
    }
}

object StatusAbnormalityManager {
    data class DamageTakenModifier(val reductionMultiplier: Double, val increaseMultiplier: Double) {
        val combinedMultiplier: Double
            get() = reductionMultiplier * increaseMultiplier
    }

    fun PlayerData.vibrationExplosion(damager: PlayerData) {
        val vibration = getStatus<Vibration>()

        if (vibration == null || vibration.power <= 0) return
        damage(vibration.power.toDouble(), DamageType.StatusAbnormality, damager)
        vibration.remove()
    }



    inline fun <reified T : StatusAbnormality> PlayerData.getStatus(): T? {
        return statusAbnormalitys.firstOrNull { it is T } as? T
    }

    inline fun <reified T : StatusAbnormality> PlayerData.getAllStatus(): List<T> {
        return statusAbnormalitys.filterIsInstance<T>()
    }

    inline fun <reified T : StatusAbnormality> PlayerData.hasStatus(): Boolean {
        return statusAbnormalitys.any { it is T }
    }

    fun PlayerData.addStatus(status: StatusAbnormality): StatusAbnormality {
        status.inject(this)
        statusAbnormalitys.add(status)
        return status
    }

    inline fun <reified T : StatusAbnormality> PlayerData.getOrCreateStatus(creator: () -> T): T {
        val existing = statusAbnormalitys.firstOrNull { it is T } as? T
        if (existing != null) return existing

        val newStatus = creator()
        newStatus.inject(this)
        statusAbnormalitys.add(newStatus)
        return newStatus
    }

    fun PlayerData.attackSpeedChanged() {
        val attackSpeedIncreases = getAllStatus<AttackSpeedIncrease>()

        val attackSpeedModifier = attackSpeedIncreases.sumOf { it.power }

        val attributeInstance = player.getAttribute(Attribute.ATTACK_SPEED) ?: return

        val baseValue = 4.0
        val newValue = baseValue * (1 + attackSpeedModifier / 100.0)
        attributeInstance.baseValue = newValue
    }

    fun PlayerData.moveSpeedChanged() {
        val moveSpeedIncreases = getAllStatus<MoveSpeedIncrease>()
        val moveSpeedDecreases = getAllStatus<MoveSpeedDecrease>()

        val increaseFactor = moveSpeedIncreases.fold(1.0) { acc, status ->
            acc * (1 + status.power / 100.0)
        }

        val decreaseFactor = moveSpeedDecreases.fold(1.0) { acc, status ->
            acc * (1 - status.power / 100.0)
        }

        val attributeInstance = player.getAttribute(Attribute.MOVEMENT_SPEED) ?: return

        val baseValue = 0.1
        val newValue = baseValue * increaseFactor * decreaseFactor
        attributeInstance.baseValue = newValue
    }

    fun PlayerData.getDamageTakenModifier(): DamageTakenModifier {
        val whenDamageReduction = getAllStatus<WhenDamageReduction>()
        val whenDamageIncrease = getAllStatus<WhenDamageIncreased>()

        val reductionFactor = whenDamageReduction.fold(1.0) { acc, status ->
            acc * (1 - status.power / 100.0)
        }.coerceAtLeast(0.0)

        val increaseFactor = whenDamageIncrease.fold(1.0) { acc, status ->
            acc * (1 + status.power / 100.0)
        }

        return DamageTakenModifier(reductionFactor, increaseFactor)
    }

    fun PlayerData.getWhenDamage(): Double {
        return getDamageTakenModifier().combinedMultiplier
    }
}
