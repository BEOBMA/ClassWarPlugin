package org.beobma.classWarPlugin.status

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager
import org.beobma.classWarPlugin.game.Game
import org.bukkit.entity.Entity

abstract class StatusAbnormality {
    protected lateinit var entityData: EntityData
    protected lateinit var entity: Entity
    protected lateinit var entityStatus: EntityStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    abstract val canRemove: Boolean

    open var power: Int = 0
    open var maxPower: Int? = null
    open var duration: Int? = null
    open var continueWhile: (() -> Boolean)? = null

    fun inject(entityData: EntityData) {
        this.entityData = entityData
        this.entity = entityData.entity
        this.entityStatus = entityData.entityStatus
        this.game = entityData.game
    }

    open fun increasePower(amount: Int) {
        val maxPower = maxPower
        power += amount
        if (maxPower != null && power > maxPower) {
            power = maxPower
        }
        onPowerChanged()
    }

    open fun updatePower(amount: Int) {
        val maxPower = maxPower
        power = amount
        if (maxPower != null && power > maxPower) {
            power = maxPower
        }
        onPowerChanged()
    }

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

    open fun increaseDuration(amount: Int) {
        duration = (duration ?: 0) + amount
        onDurationChanged()
    }

    open fun updateDuration(amount: Int?) {
        duration = amount
        onDurationChanged()
    }

    open fun decreaseDuration(amount: Int) {
        val current = (duration ?: 0) - amount
        duration = current.coerceAtLeast(0)
        onDurationChanged()
    }

    open fun remove() {
        stopDurationTicking()
        if (canRemove) {
            entityData.statusAbnormalitys.remove(this@StatusAbnormality)
            onRemoveStatusAbnormality()
        } else {
            power = 0
        }
    }

    fun setContinueWhileIf(predicate: () -> Boolean) {
        this.continueWhile = predicate
        updateDuration(null)
    }

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
    }

    open fun onPowerChanged() {
        if (power <= 0) {
            expireStatus()
            return
        }
        refreshDurationTask()
    }

    open fun onRemoveStatusAbnormality() {}

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
        tickStatus()
    }

    private fun tickStatus() {
        if (!shouldTick()) {
            stopDurationTicking()
            return
        }

        if (continueWhile != null && !continueWhile!!.invoke()) {
            expireStatus()
            return
        }

        val currentDuration = duration
        if (currentDuration != null) {
            val nextDuration = currentDuration - 1
            duration = nextDuration
            if (nextDuration <= 0) {
                expireStatus()
            }
        }
    }

    private fun expireStatus() {
        stopDurationTicking()
        if (canRemove) {
            entityData.statusAbnormalitys.remove(this@StatusAbnormality)
            onRemoveStatusAbnormality()
        } else {
            power = 0
        }
    }
}
