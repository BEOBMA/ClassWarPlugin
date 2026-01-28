package org.beobma.classWarPlugin.status

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

abstract class StatusAbnormality {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    abstract val canRemove: Boolean

    open var power: Int = 0
    open var maxPower: Int? = null
    open var duration: Int? = null
    open var continueWhile: (() -> Boolean)? = null

    private var durationTask: BukkitTask? = null

    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
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
        stopDurationTask()
        if (canRemove) {
            playerData.statusAbnormalitys.remove(this@StatusAbnormality)
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
            startDurationTask()
        } else {
            stopDurationTask()
        }
    }

    private fun shouldTick(): Boolean {
        return duration != null || continueWhile != null
    }

    private fun startDurationTask() {
        if (durationTask != null) return
        val task = object : BukkitRunnable() {
            override fun run() {
                tickStatus()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L)
        durationTask = playerData.trackTask(task)
    }

    private fun stopDurationTask() {
        durationTask?.cancel()
        durationTask = null
    }

    private fun tickStatus() {
        if (!shouldTick()) {
            stopDurationTask()
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
        stopDurationTask()
        if (canRemove) {
            playerData.statusAbnormalitys.remove(this@StatusAbnormality)
            onRemoveStatusAbnormality()
        } else {
            power = 0
        }
    }
}
