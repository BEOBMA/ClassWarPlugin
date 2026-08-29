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
import org.beobma.classWarPlugin.gameClass.list.Mercurius
import org.beobma.classWarPlugin.gameClass.list.PlanetPowerRegistry

/**
 * 상태이상의 생성·조회·밸런스 적용과 초 단위 공용 틱 작업을 관리한다.
 * 모든 상태가 해제되면 공용 Bukkit 작업도 자동으로 중지된다.
 */
object StatusAbnormalityManager {
    private val tickingStatuses: MutableSet<StatusAbnormality> = HashSet()
    private val originalAttackSpeeds: MutableMap<java.util.UUID, Double> = HashMap()
    private val originalMoveSpeeds: MutableMap<java.util.UUID, Double> = HashMap()
    private var tickingTask: BukkitTask? = null

    /** 감소와 증가 효과를 분리해 누적한 받는 피해 배율이다. */
    data class DamageTakenModifier(val reductionMultiplier: Double, val increaseMultiplier: Double) {
        val combinedMultiplier: Double
            get() = reductionMultiplier * increaseMultiplier
    }

    /**
     * 상태 세기와 지속시간을 한 번에 적용한다.
     * 클래스 고유 자원이 아닌 상태에는 시전자 클래스 배율이 적용된다.
     *
     * @param duration 적용할 지속시간(초)
     * @param powerDelta 현재 세기에 더할 값
     * @param powerSet 현재 세기를 교체할 값. [powerDelta]보다 먼저 적용된다.
     */
    fun StatusAbnormality.applyStatus(duration: Int? = null, powerDelta: Int? = null, powerSet: Int? = null) {
        val caster = balanceCasterData()
        val shouldBalance = !isClassMechanic
        val balancedPowerSet = powerSet?.let {
            if (shouldBalance && showPower) ClassBalanceManager.scaleStatusPower(caster, it) else it
        }
        val balancedPowerDelta = powerDelta?.let {
            if (shouldBalance && showPower) ClassBalanceManager.scaleStatusPower(caster, it) else it
        }
        val balancedDuration = duration?.let {
            if (shouldBalance) ClassBalanceManager.scaleStatusDuration(caster, it) else it
        }
        balancedPowerSet?.let { updatePower(it) }
        balancedPowerDelta?.let { increasePower(it) }
        if (balancedDuration != null) {
            when (durationMode) {
                StatusDurationMode.Refresh -> updateDuration(balancedDuration)
                StatusDurationMode.Extend -> increaseDuration(balancedDuration)
                StatusDurationMode.Ignore -> return
            }
        }
    }

    /** 대상에 부착된 [T] 중 첫 번째 상태를 반환한다. */
    inline fun <reified T : StatusAbnormality> EntityData.getStatus(): T? {
        return statusAbnormalitys.firstOrNull { it is T } as? T
    }

    /** 대상에 부착된 모든 [T] 상태를 새 목록으로 반환한다. */
    inline fun <reified T : StatusAbnormality> EntityData.getAllStatus(): List<T> {
        return statusAbnormalitys.filterIsInstance<T>()
    }

    /** 대상에 [T]가 하나 이상 부착되어 있는지 반환한다. */
    inline fun <reified T : StatusAbnormality> EntityData.hasStatus(): Boolean {
        return statusAbnormalitys.any { it is T }
    }

    /** [status]에 대상과 효과 출처를 주입한 뒤 목록에 추가한다. 중복은 허용한다. */
    fun EntityData.addStatus(status: StatusAbnormality, victimData: PlayerData): StatusAbnormality {
        status.inject(this, victimData)
        statusAbnormalitys.add(status)
        return status
    }

    /** 기존 [T]를 재사용하거나 [creator]로 생성해 주입·등록한다. */
    inline fun <reified T : StatusAbnormality> EntityData.getOrCreateStatus(victimData: PlayerData, creator: () -> T): T {
        val existing = statusAbnormalitys.firstOrNull { it is T } as? T
        if (existing != null) return existing

        val newStatus = creator()
        newStatus.inject(this, victimData)
        statusAbnormalitys.add(newStatus)
        return newStatus
    }

    /** 공격 속도 상태를 모두 결합해 원본 공격 속성 기준으로 다시 계산한다. */
    fun EntityData.attackSpeedChanged() {
        var increaseFactor = 1.0
        var decreaseFactor = 1.0
        for (status in statusAbnormalitys) {
            when (status) {
                is AttackSpeedIncrease -> increaseFactor *= (1 + status.power / 100.0)
                is AttackSpeedDecrease -> decreaseFactor *= (1 - status.power / 100.0)
            }
        }

        val entity = entity
        if (entity is LivingEntity) {
            val attributeInstance = entity.getAttribute(Attribute.ATTACK_SPEED) ?: return
            val hasModifier = statusAbnormalitys.any { it is AttackSpeedIncrease || it is AttackSpeedDecrease }
            if (!hasModifier) {
                originalAttackSpeeds.remove(entity.uniqueId)?.let { attributeInstance.baseValue = it }
                return
            }
            val baseValue = originalAttackSpeeds.getOrPut(entity.uniqueId) { attributeInstance.baseValue }
            val newValue = baseValue * increaseFactor * decreaseFactor.coerceAtLeast(0.0)
            attributeInstance.baseValue = newValue
            return
        }
    }

    /** 이동 속도 상태를 모두 결합해 원본 이동 속성 기준으로 다시 계산한다. */
    fun EntityData.moveSpeedChanged() {
        var increaseFactor = 1.0
        var decreaseFactor = 1.0
        val ignoresDecrease = (this as? PlayerData)?.let {
            PlanetPowerRegistry.hasPower(it, Mercurius::class.java)
        } == true
        for (status in statusAbnormalitys) {
            when (status) {
                is MoveSpeedIncrease -> increaseFactor *= (1 + status.power / 100.0)
                is MoveSpeedDecrease -> if (!ignoresDecrease) decreaseFactor *= (1 - status.power / 100.0)
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

    /** 대상의 모든 받는 피해 감소·증가 상태를 각각 곱해 결합한다. */
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

    /** 현재 표시 가능한 상태를 클래스 자원과 일반 상태로 나눠 액션바에 전송한다. */
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
        fun List<StatusAbnormality>.line(): String = filter { it.showInActionBar }.sortedBy { it.name }
            .joinToString(" <dark_gray> | </dark_gray> ") { it.actionBarText() }

        val mechanics = statuses.filter { it.isClassMechanic }.line()
        val abnormalities = statuses.filterNot { it.isClassMechanic }.line()
        return listOf(mechanics, abnormalities)
            .filter { it.isNotBlank() }
            .joinToString(" <aqua><bold>|</bold></aqua> ")
    }
}
