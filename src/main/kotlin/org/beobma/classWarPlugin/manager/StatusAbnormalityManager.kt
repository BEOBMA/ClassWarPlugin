package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.UtilManager.miniMessage
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import net.kyori.adventure.text.Component
import org.beobma.classWarPlugin.status.StatusDurationMode
import org.beobma.classWarPlugin.status.handler.WhenDamageHandler
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

object StatusAbnormalityManager {
    private val tickingStatuses: MutableSet<StatusAbnormality> = HashSet()
    private val originalAttackSpeeds: MutableMap<java.util.UUID, Double> = HashMap()
    private val originalMoveSpeeds: MutableMap<java.util.UUID, Double> = HashMap()
    private var tickingTask: BukkitTask? = null

    data class DamageTakenModifier(val reductionMultiplier: Double, val increaseMultiplier: Double) {
        val combinedMultiplier: Double
            get() = reductionMultiplier * increaseMultiplier
    }

    /**
     * @param duration 지속 시간
     * @param powerDelta 상태이상의 기본 수치 값에 더할 값
     * @param powerSet 상태이상의 기본 수치 값에 덮어 씌울 값
     */
    fun StatusAbnormality.applyStatus(duration: Int? = null, powerDelta: Int? = null, powerSet: Int? = null) {
        powerSet?.let { updatePower(it) }
        powerDelta?.let { increasePower(it) }
        if (duration != null) {
            when (durationMode) {
                StatusDurationMode.Refresh -> updateDuration(duration)
                StatusDurationMode.Extend -> increaseDuration(duration)
                StatusDurationMode.Ignore -> return
            }
        }
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

    fun EntityData.addStatus(status: StatusAbnormality, victimData: PlayerData): StatusAbnormality {
        status.inject(this, victimData)
        statusAbnormalitys.add(status)
        return status
    }

    inline fun <reified T : StatusAbnormality> EntityData.getOrCreateStatus(victimData: PlayerData, creator: () -> T): T {
        val existing = statusAbnormalitys.firstOrNull { it is T } as? T
        if (existing != null) return existing

        val newStatus = creator()
        newStatus.inject(this, victimData)
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
            val hasModifier = statusAbnormalitys.any { it is AttackSpeedIncrease }
            if (!hasModifier) {
                originalAttackSpeeds.remove(entity.uniqueId)?.let { attributeInstance.baseValue = it }
                return
            }
            val baseValue = originalAttackSpeeds.getOrPut(entity.uniqueId) { attributeInstance.baseValue }
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
            val hasModifier = statusAbnormalitys.any { it is MoveSpeedIncrease || it is MoveSpeedDecrease }
            if (!hasModifier) {
                originalMoveSpeeds.remove(entity.uniqueId)?.let { attributeInstance.baseValue = it }
                return
            }
            val baseValue = originalMoveSpeeds.getOrPut(entity.uniqueId) { attributeInstance.baseValue }
            val newValue = baseValue * increaseFactor * decreaseFactor
            attributeInstance.baseValue = newValue
            return
        }
    }

    fun EntityData.getDamageTakenModifier(): DamageTakenModifier {
        var reductionFactor = 1.0
        var increaseFactor = 1.0
        for (status in statusAbnormalitys) {
            if (status !is WhenDamageHandler) continue
            when (status) {
                is WhenDamageReduction -> reductionFactor *= (1 - status.power / 100.0)
                is WhenDamageIncreased -> increaseFactor *= (1 + status.power / 100.0)
            }
        }
        reductionFactor = reductionFactor.coerceAtLeast(0.0)

        return DamageTakenModifier(reductionFactor, increaseFactor)
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
        for (status in statuses.toList()) {
            unregisterTickingStatus(status)
            status.cleanupFromManager()
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

    fun PlayerData.updateStatusActionBar() {
        val statusMessage = buildStatusActionBarMessage(statusAbnormalitys)
        if (statusMessage.isBlank()) {
            player.sendActionBar(Component.empty())
            return
        }
        player.sendActionBar(miniMessage.deserialize(statusMessage))
    }

    private fun buildStatusActionBarMessage(statuses: List<StatusAbnormality>): String {
        if (statuses.isEmpty()) return ""
        fun List<StatusAbnormality>.line(): String = sortedBy { it.name }
            .joinToString(" <dark_gray> | </dark_gray> ") { it.actionBarText() }

        val mechanics = statuses.filter { it.isClassMechanic }.line()
        val abnormalities = statuses.filterNot { it.isClassMechanic }.line()
        return listOf(mechanics, abnormalities)
            .filter { it.isNotBlank() }
            .joinToString(" <aqua><bold>|</bold></aqua> ")
    }
}
