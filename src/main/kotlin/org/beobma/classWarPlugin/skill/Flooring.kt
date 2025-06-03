package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.player.PlayerStatus
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

abstract class Flooring {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract var location: Location
    abstract var radius: Double
    abstract var targetType: TargetType

    open var time: Int? = null
    open var continueWhile: (() -> Boolean)? = null

    private var durationTask: BukkitTask? = null

    fun inject(playerData: PlayerData) {
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.playerStatus
        this.game = playerData.game
    }

    fun setContinueWhileIf(predicate: () -> Boolean) {
        this.continueWhile = predicate
        time = null
    }

    open fun onFlooringContinue(location: Location) {}
    open fun onFlooringPlayerHit(hitPlayerData: PlayerData, location: Location) {}
    open fun onFlooringPlayerOut(hitPlayerData: PlayerData, location: Location) {}
    open fun onFlooringEnd() {}

    fun spawnFlooring(playerData: PlayerData) {
        inject(playerData)

        val game = game
        val currentLocation = location.clone()
        val time = time
        var ticks = 0

        var previousTargets: Set<PlayerData> = emptySet()

        val task = object : BukkitRunnable() {
            override fun run() {
                if (time == null) {
                    if (continueWhile != null && !continueWhile!!.invoke()) {
                        onFlooringEnd()
                        cancel()
                        return
                    }
                } else {
                    if (ticks++ >= time * 20) {
                        onFlooringEnd()
                        cancel()
                        return
                    }
                }

                val currentTargets = game.playerDatas.filter {
                    it != playerData &&
                            it.playerStatus.isSkillTargeting &&
                            it.player.location.distanceSquared(currentLocation) <= radius * radius &&
                            when (targetType) {
                                TargetType.Team -> it.team == playerData.team
                                TargetType.Enemy -> it.team != playerData.team
                                TargetType.All -> true
                            }
                }.toSet()

                val exitedTargets = previousTargets - currentTargets
                for (exited in exitedTargets) {
                    onFlooringPlayerOut(exited, currentLocation)
                }

                onFlooringContinue(currentLocation)
                for (target in currentTargets) {
                    onFlooringPlayerHit(target, currentLocation)
                }

                previousTargets = currentTargets
            }
        }.runTaskTimer(ClassWarPlugin.Companion.instance, 0L, 1L)

        durationTask = task
    }
}