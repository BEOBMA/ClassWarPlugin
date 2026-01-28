package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.TargetType.All
import org.beobma.classWarPlugin.util.TargetType.Enemy
import org.beobma.classWarPlugin.util.TargetType.Team
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

abstract class Meteor(

) {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract var location: Location
    abstract var speed: Double
    abstract var isWallHit: Boolean
    abstract var targetType: TargetType

    open var time: Int? = null
    open var continueWhile: (() -> Boolean)? = null

    private var durationTask: BukkitTask? = null

    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }

    open fun onMeteorMove(location: Location) {}
    open fun onMeteorPlayerHit(hitPlayerData: PlayerData, location: Location) {}
    open fun onMeteorBlockHit(hitBlock: Block, location: Location) {}

    fun spawnMeteor(playerData: PlayerData) {
        inject(playerData)
        val currentLocation = location.clone()
        val time = time
        var ticks = 0

        val task = object : BukkitRunnable() {
            override fun run() {
                if (time == null) {
                    if (continueWhile != null && !continueWhile!!.invoke()) {
                        cancel()
                        return
                    }
                } else {
                    if (ticks++ >= time * 20) {
                        cancel()
                        return
                    }
                }

                if (isWallHit && currentLocation.block.type.isSolid) {
                    onMeteorBlockHit(currentLocation.block, currentLocation)
                    cancel()
                    return
                }

                val collidedPlayerData = game.playerDatas
                    .filter { it != playerData && it.entityStatus.isSkillTargeting }
                    .firstOrNull { targetData ->
                        targetData.player.location.distanceSquared(currentLocation) <= 1.0 &&
                                when (targetType) {
                                    Team -> targetData.team == playerData.team
                                    Enemy -> targetData.team != playerData.team
                                    All -> true
                                }
                    }

                if (collidedPlayerData != null) {
                    onMeteorPlayerHit(collidedPlayerData, currentLocation)
                    cancel()
                    return
                }

                onMeteorMove(currentLocation)
                currentLocation.y -= speed
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        durationTask = playerData.trackTask(task)
    }
}
