package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
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
import org.bukkit.util.BoundingBox
import java.util.UUID

abstract class Projectile {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract var location: Location
    abstract var targetType: TargetType
    abstract var speed: Double
    abstract var isWallHit: Boolean
    abstract var isPlayerHit: Boolean
    abstract val isPlayerHitRemove: Boolean

    open val isFlatMove: Boolean = false
    open var xSize: Double = 0.3
    open var ySize: Double = 0.3
    open var zSize: Double = 0.3

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

    fun setContinueWhileIf(predicate: () -> Boolean) {
        this.continueWhile = predicate
        time = null
    }


    open fun onProjectileMove(location: Location) {}

    open fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {}

    open fun onProjectileBlockHit(hitBlock: Block, location: Location) {}

    fun spawnProjectile(playerData: PlayerData) {
        inject(playerData)
        val game = game
        val time = time
        if (isFlatMove) location.pitch = 0F

        val direction = location.direction.normalize().multiply(speed)
        val currentLocation = location.clone()
        var ticks = 0
        val trainingCandidates: MutableList<EntityData> = ArrayList()
        val trainingCandidateIds: MutableSet<UUID> = HashSet()

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
                    onProjectileBlockHit(currentLocation.block, currentLocation)
                    cancel()
                    return
                }

                if (isPlayerHit) {
                    val isTraining = PlayerTagManager.hasTag(player, "isTraining")
                    val targetCandidates = if (isTraining) {
                        trainingCandidates.clear()
                        trainingCandidateIds.clear()
                        game.playerDatas.forEach { data ->
                            val entityId = data.entity.uniqueId
                            if (trainingCandidateIds.add(entityId)) {
                                trainingCandidates.add(data)
                            }
                        }
                        for (mannequin in player.world.entities) {
                            if (!mannequin.isMannequin()) continue
                            val data = game.playerDatas.find { it.entity == mannequin }
                                ?: DummyEntityData(mannequin, game).also { game.playerDatas.add(it) }
                            val entityId = data.entity.uniqueId
                            if (trainingCandidateIds.add(entityId)) {
                                trainingCandidates.add(data)
                            }
                        }
                        trainingCandidates
                    } else {
                        game.playerDatas
                    }
                    var collidedEntityData: EntityData? = null
                    for (targetData in targetCandidates) {
                        if (targetData == playerData || !targetData.entityStatus.isSkillTargeting) continue
                        val bb = targetData.entity.boundingBox.expand(xSize, ySize, zSize)
                        if (!bb.contains(currentLocation.x, currentLocation.y, currentLocation.z)) continue
                        val isValidTarget = when (targetType) {
                            Team -> targetData.entity.isMannequin() && isTraining ||
                                (targetData is PlayerData && targetData.team == playerData.team)
                            Enemy -> targetData.entity.isMannequin() && isTraining ||
                                (targetData is PlayerData && targetData.team != playerData.team)
                            All -> true
                        }
                        if (isValidTarget) {
                            collidedEntityData = targetData
                            break
                        }
                    }

                    if (collidedEntityData != null) {
                        onProjectileEntityHit(collidedEntityData, currentLocation)
                        if (isPlayerHitRemove) {
                            cancel()
                            return
                        }
                    }
                }

                onProjectileMove(currentLocation)
                currentLocation.add(direction)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        durationTask = playerData.trackTask(task)
    }
}
