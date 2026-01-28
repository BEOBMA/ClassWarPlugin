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

        var previousTargets: Set<EntityData> = emptySet()

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
                    val candidates = game.playerDatas.toMutableList()
                    player.world.entities.filter { it.isMannequin() }.forEach { mannequin ->
                        val data = game.playerDatas.find { it.entity == mannequin }
                            ?: DummyEntityData(mannequin, game).also { game.playerDatas.add(it) }
                        candidates.add(data)
                    }
                    candidates.distinctBy { it.entity.uniqueId }
                } else {
                    game.playerDatas
                }

                val currentTargets = targetCandidates.filter {
                    it != playerData &&
                            it.entityStatus.isSkillTargeting &&
                            it.entity.location.distanceSquared(currentLocation) <= radius * radius &&
                            when (targetType) {
                                TargetType.Team -> it.entity.isMannequin() && isTraining ||
                                    (it is PlayerData && it.team == playerData.team)
                                TargetType.Enemy -> it.entity.isMannequin() && isTraining ||
                                    (it is PlayerData && it.team != playerData.team)
                                TargetType.All -> true
                            }
                }.toSet()

                val exitedTargets = previousTargets - currentTargets
                for (exited in exitedTargets) {
                    onFlooringEntityOut(exited, currentLocation)
                }

                onFlooringContinue(currentLocation)
                for (target in currentTargets) {
                    onFlooringEntityHit(target, currentLocation)
                }

                previousTargets = currentTargets
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)

        durationTask = playerData.trackTask(task)
    }
}
