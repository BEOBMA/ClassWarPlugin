package org.beobma.classWarPlugin.status

import org.beobma.classWarPlugin.ability.AbilityScope
import org.beobma.classWarPlugin.ability.AbilityExecution

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.bukkit.entity.Entity

/**
 * 전투 엔티티에 부착되는 상태이상과 클래스 고유 자원의 기반 형식이다.
 *
 * [duration]은 초 단위이며 `null`이면 시간 제한이 없다. [power] 또는 유한한 [duration]이
 * `0` 이하가 되면 만료된다. [canRemove]가 `false`인 구현은 목록에 남고 세기만 `0`으로 초기화된다.
 * 인스턴스를 사용하기 전에 [inject]로 대상과 효과 출처를 연결해야 한다.
 */
abstract class StatusAbnormality {
    protected lateinit var entityData: EntityData
    protected lateinit var casterData: PlayerData
    protected val entity: Entity get() = entityData.entity
    var effectSource: AbilityScope? = null
        private set
    private val owners = mutableSetOf<AbilityScope>()

    /** Shared self resources remain attached until the last using class is removed. */
    fun retain(scope: AbilityScope) {
        if (!owners.add(scope)) return
        scope.resources.own(isAlive = { this in entityData.statusAbnormalitys }) {
            owners.remove(scope)
            if (owners.isEmpty()) cleanupFromManager()
            else if (effectSource === scope) effectSource = owners.first()
        }
    }

    fun <T> fromSource(body: () -> T): T = AbilityExecution.with(effectSource, body)
    protected lateinit var entityStatus: EntityStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    abstract val canRemove: Boolean

    open var power: Int = 0
    open var maxPower: Int? = null
    open val showMaxPower = true
    open val showPower = true
    open val showInActionBar = true
    open val isClassMechanic = false
    open var duration: Int? = null
    open val durationMode: StatusDurationMode = StatusDurationMode.Refresh
    open var continueWhile: (() -> Boolean)? = null

    /** 상태의 대상 [entityData]와 밸런스 계산에 사용할 효과 출처 [victimData]를 연결한다. */
    fun inject(entityData: EntityData, victimData: PlayerData) {
        this.entityData = entityData
        this.entityStatus = entityData.entityStatus
        this.game = entityData.game
        this.casterData = victimData
        effectSource = AbilityExecution.current
            ?.takeIf { it.playerData === victimData }

    }

    /** 재접속 등으로 대상 엔티티 객체가 바뀌었을 때 출처는 유지하고 대상만 다시 연결한다. */
    fun rebindEntity(entityData: EntityData) {
        this.entityData = entityData
        this.entityStatus = entityData.entityStatus
        this.game = entityData.game
    }

    internal fun balanceCasterData(): PlayerData? = if (::casterData.isInitialized) casterData else null

    /** 현재 세기에 [amount]를 더하고 [maxPower]가 있으면 상한을 적용한다. */
    open fun increasePower(amount: Int) {
        val maxPower = maxPower
        power += amount
        if (maxPower != null && power > maxPower) {
            power = maxPower
        }
        onPowerChanged()
    }

    /** 현재 세기를 [amount]로 교체하고 [maxPower]가 있으면 상한을 적용한다. */
    open fun updatePower(amount: Int) {
        val maxPower = maxPower
        power = amount
        if (maxPower != null && power > maxPower) {
            power = maxPower
        }
        onPowerChanged()
    }

    /** 현재 세기에서 [amount]를 빼며 결과를 `0` 이상으로 제한한다. */
    open fun decreasePower(amount: Int) {
        power = (power - amount).coerceAtLeast(0)
        onPowerChanged()
    }

    open fun increaseMaxPower(amount: Int) {
        maxPower = (maxPower ?: 0) + amount
    }

    open fun decreaseMaxPower(amount: Int) {
        val current = (maxPower ?: 0) - amount
        maxPower = current.coerceAtLeast(0)
    }

    /** 지속시간에 [amount]초를 더한다. 무기한 상태는 `0`초를 기준으로 전환된다. */
    open fun increaseDuration(amount: Int) {
        duration = (duration ?: 0) + amount
        onDurationChanged()
    }

    /** 지속시간을 [amount]초로 교체한다. `null`은 시간 제한 없음을 뜻한다. */
    open fun updateDuration(amount: Int?) {
        duration = amount
        onDurationChanged()
    }

    /** 지속시간에서 [amount]초를 빼며 결과를 `0` 이상으로 제한한다. */
    open fun decreaseDuration(amount: Int) {
        val current = (duration ?: 0) - amount
        duration = current.coerceAtLeast(0)
        onDurationChanged()
    }

    /** 상태를 명시적으로 해제하고 제거 콜백 및 액션바 갱신을 수행한다. */
    open fun remove() {
        stopDurationTicking()
        if (canRemove) {
            entityData.statusAbnormalitys.remove(this@StatusAbnormality)
            power = 0
            fromSource { onRemoveStatusAbnormality() }
        } else {
            power = 0
        }
        notifyStatusChanged()
    }

    /** 시간 제한을 제거하고 [predicate]가 참인 동안 상태를 유지한다. */
    fun setContinueWhileIf(predicate: () -> Boolean) {
        this.continueWhile = predicate
        updateDuration(null)
    }

    /** 지속시간 변경 후 만료 여부와 공용 틱 등록 상태를 갱신한다. */
    open fun onDurationChanged() {
        if (power <= 0) {
            expireStatus()
            return
        }
        val currentDuration = duration
        if (currentDuration != null && currentDuration <= 0) {
            expireStatus()
            return
        }

        refreshDurationTask()
        notifyStatusChanged()
    }

    /** 세기 변경 후 만료 여부와 공용 틱 등록 상태를 갱신한다. */
    open fun onPowerChanged() {
        if (power <= 0) {
            expireStatus()
            return
        }
        refreshDurationTask()
        notifyStatusChanged()
    }

    /** 상태가 대상에서 제거될 때 파생 효과를 정리하는 확장 지점이다. */
    open fun onRemoveStatusAbnormality() {}

    /** 영역 효과처럼 상태를 제거하지 않고 남은 지속시간만 일시 정지할 때 재정의한다. */
    open fun isDurationPaused(): Boolean = false

    /** 현재 세기와 남은 시간을 포함한 MiniMessage 액션바 조각을 만든다. */
    open fun actionBarText(): String {
        val durationText = duration?.let { "${it}s" } ?: "∞"
        val durationLabel = "<dark_gray>|</dark_gray><yellow>$durationText</yellow>"
        val powerLabel = if (showPower) "<gold>${power}</gold>" else ""
        val maxPowerLabel =
            if (showMaxPower) maxPower?.let { "<dark_gray>/</dark_gray><gold>${it}</gold>" } ?: "" else ""
        return "$name: $powerLabel$maxPowerLabel$durationLabel"
    }

    private fun refreshDurationTask() {
        if (shouldTick()) {
            startDurationTicking()
        } else {
            stopDurationTicking()
        }
    }

    private fun shouldTick(): Boolean {
        return duration != null || continueWhile != null
    }

    private fun startDurationTicking() {
        StatusAbnormalityManager.registerTickingStatus(this)
    }

    private fun stopDurationTicking() {
        StatusAbnormalityManager.unregisterTickingStatus(this)
    }

    internal fun tickStatusFromManager() {
        fromSource { tickStatus() }
    }

    internal fun cleanupFromManager() {
        if (this !in entityData.statusAbnormalitys) return
        stopDurationTicking()
        entityData.statusAbnormalitys.remove(this)
        power = 0
        fromSource { onRemoveStatusAbnormality() }
    }

    private fun tickStatus() {
        if (game.isPaused) return
        if (!shouldTick()) {
            stopDurationTicking()
            return
        }

        if (continueWhile?.invoke() == false) {
            expireStatus()
            return
        }

        val currentDuration = duration
        if (currentDuration != null) {
            if (isDurationPaused()) return
            val nextDuration = currentDuration - 1
            duration = nextDuration
            if (nextDuration <= 0) {
                expireStatus()
                return
            }
            notifyStatusChanged()
        }
    }

    private fun expireStatus() {
        stopDurationTicking()
        if (canRemove) {
            entityData.statusAbnormalitys.remove(this@StatusAbnormality)
            power = 0
            fromSource { onRemoveStatusAbnormality() }
        } else {
            power = 0
        }
        notifyStatusChanged()
    }

    private fun notifyStatusChanged() {
        val playerData = entityData as? PlayerData ?: return
        playerData.updateStatusActionBar()
    }
}
