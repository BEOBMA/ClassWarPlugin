package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask


// 갱신, 연장
enum class StatusDurationMode {
    Refresh,
    Extend
}

object StatusAbnormalityManager {
    private val tickingStatuses: MutableSet<StatusAbnormality> = HashSet()
    private var tickingTask: BukkitTask? = null

    data class DamageTakenModifier(val reductionMultiplier: Double, val increaseMultiplier: Double) {
        val combinedMultiplier: Double
            get() = reductionMultiplier * increaseMultiplier
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

    fun EntityData.vibrationExplosion(damager: PlayerData) {
        val vibration = getStatus<Vibration>()

        if (vibration == null || vibration.power <= 0) return
        damage(vibration.power.toDouble(), DamageType.StatusAbnormality, damager)
        vibration.remove()
    }



    inline fun <reified T : StatusAbnormality> EntityData.getStatus(): T? {
        return statusAbnormalitys.firstOrNull { it is T } as? T
    }

    inline fun <reified T : StatusAbnormality> EntityData.getAllStatus(): List<T> {
        return statusAbnormalitys.filterIsInstance<T>()
    }

    inline fun <reified T : StatusAbnormality> EntityData.hasStatus(): Boolean {
        return statusAbnormalitys.any { it is T }
    }

    fun EntityData.addStatus(status: StatusAbnormality): StatusAbnormality {
        status.inject(this)
        statusAbnormalitys.add(status)
        return status
    }

    inline fun <reified T : StatusAbnormality> EntityData.getOrCreateStatus(creator: () -> T): T {
        val existing = statusAbnormalitys.firstOrNull { it is T } as? T
        if (existing != null) return existing

        val newStatus = creator()
        newStatus.inject(this)
        statusAbnormalitys.add(newStatus)
        return newStatus
    }

    fun EntityData.attackSpeedChanged() {
        var attackSpeedModifier = 0
        for (status in statusAbnormalitys) {
            if (status is AttackSpeedIncrease) {
                attackSpeedModifier += status.power
            }
        }

        val entity = entity
        if (entity is LivingEntity) {
            val attributeInstance = entity.getAttribute(Attribute.ATTACK_SPEED) ?: return
            val baseValue = 4.0
            val newValue = baseValue * (1 + attackSpeedModifier / 100.0)
            attributeInstance.baseValue = newValue
            return
        }
    }

    fun EntityData.moveSpeedChanged() {
        var increaseFactor = 1.0
        var decreaseFactor = 1.0
        for (status in statusAbnormalitys) {
            when (status) {
                is MoveSpeedIncrease -> increaseFactor *= (1 + status.power / 100.0)
                is MoveSpeedDecrease -> decreaseFactor *= (1 - status.power / 100.0)
            }
        }

        val entity = entity
        if (entity is LivingEntity) {
            val attributeInstance = entity.getAttribute(Attribute.MOVEMENT_SPEED) ?: return

            val baseValue = 0.1
            val newValue = baseValue * increaseFactor * decreaseFactor
            attributeInstance.baseValue = newValue
            return
        }
    }

    fun EntityData.getDamageTakenModifier(): DamageTakenModifier {
        var reductionFactor = 1.0
        var increaseFactor = 1.0
        for (status in statusAbnormalitys) {
            when (status) {
                is WhenDamageReduction -> reductionFactor *= (1 - status.power / 100.0)
                is WhenDamageIncreased -> increaseFactor *= (1 + status.power / 100.0)
            }
        }
        reductionFactor = reductionFactor.coerceAtLeast(0.0)

        return DamageTakenModifier(reductionFactor, increaseFactor)
    }

    fun EntityData.getWhenDamage(): Double {
        return getDamageTakenModifier().combinedMultiplier
    }

    internal fun registerTickingStatus(status: StatusAbnormality) {
        if (!tickingStatuses.add(status)) return
        ensureTickingTask()
    }

    internal fun unregisterTickingStatus(status: StatusAbnormality) {
        if (!tickingStatuses.remove(status)) return
        if (tickingStatuses.isEmpty()) {
            stopTickingTask()
        }
    }

    internal fun unregisterAllTickingStatuses(statuses: Iterable<StatusAbnormality>) {
        for (status in statuses) {
            unregisterTickingStatus(status)
        }
    }

    private fun ensureTickingTask() {
        if (tickingTask != null) return
        val task = object : BukkitRunnable() {
            override fun run() {
                if (tickingStatuses.isEmpty()) {
                    cancel()
                    tickingTask = null
                    return
                }
                val snapshot = tickingStatuses.toList()
                for (status in snapshot) {
                    status.tickStatusFromManager()
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L)
        tickingTask = task
    }

    private fun stopTickingTask() {
        tickingTask?.cancel()
        tickingTask = null
    }
}
