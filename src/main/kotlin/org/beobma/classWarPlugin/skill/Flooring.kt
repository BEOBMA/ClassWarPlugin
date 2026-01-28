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
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

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

    open fun onFlooringContinue(location: Location) {}
    open fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {}
    open fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {}
    open fun onFlooringEnd() {}

    fun spawnFlooring(playerData: PlayerData) {
        inject(playerData)

        val game = game
        val currentLocation = location.clone()
        val time = time
        var ticks = 0

        var previousTargets: MutableSet<EntityData> = HashSet()
        var currentTargets: MutableSet<EntityData> = HashSet()
        val trainingCandidates: MutableList<EntityData> = ArrayList()
        val trainingCandidateIds: MutableSet<UUID> = HashSet()

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

                currentTargets.clear()
                val radiusSquared = radius * radius
                for (targetData in targetCandidates) {
                    if (targetData == playerData || !targetData.entityStatus.isSkillTargeting) continue
                    if (targetData.entity.location.distanceSquared(currentLocation) > radiusSquared) continue
                    val isValidTarget = when (targetType) {
                        TargetType.Team -> targetData.entity.isMannequin() && isTraining ||
                            (targetData is PlayerData && targetData.team == playerData.team)
                        TargetType.Enemy -> targetData.entity.isMannequin() && isTraining ||
                            (targetData is PlayerData && targetData.team != playerData.team)
                        TargetType.All -> true
                    }
                    if (isValidTarget) {
                        currentTargets.add(targetData)
                    }
                }

                for (exited in previousTargets) {
                    if (!currentTargets.contains(exited)) {
                        onFlooringEntityOut(exited, currentLocation)
                    }
                }

                onFlooringContinue(currentLocation)
                for (target in currentTargets) {
                    onFlooringEntityHit(target, currentLocation)
                }

                val previousSwap = previousTargets
                previousTargets = currentTargets
                currentTargets = previousSwap
                currentTargets.clear()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)

        durationTask = playerData.trackTask(task)
    }
}
