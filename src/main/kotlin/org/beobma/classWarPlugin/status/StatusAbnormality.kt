package org.beobma.classWarPlugin.status

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.player.PlayerStatus
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
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.playerStatus
        this.game = playerData.game
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
        durationTask?.cancel()
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
        onDurationChanged()
    }

    open fun onDurationChanged() {
        durationTask?.cancel()

        // 조건 지속 검사
        if (duration == null) {
            durationTask = object : BukkitRunnable() {
                override fun run() {
                    if (continueWhile != null && !continueWhile!!.invoke()) {
                        if (canRemove) {
                            playerData.statusAbnormalitys.remove(this@StatusAbnormality)
                            onRemoveStatusAbnormality()
                        } else {
                            power = 0
                        }
                        this.cancel()
                    }
                    if (power <= 0) {
                        this.cancel()
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L)
            return
        }

        // 지속 시간 검사
        val dur = duration ?: return
        if (dur <= 0) return
        durationTask = object : BukkitRunnable() {
            override fun run() {
                if ((duration ?: 0) <= 0) {
                    if (canRemove) {
                        playerData.statusAbnormalitys.remove(this@StatusAbnormality)
                    } else {
                        power = 0
                    }
                    this.cancel()
                    return
                }

                if (power <= 0) {
                    this.cancel()
                    return
                }

                decreaseDuration(1)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L)
    }

    open fun onPowerChanged() {}

    open fun onRemoveStatusAbnormality() {}
}