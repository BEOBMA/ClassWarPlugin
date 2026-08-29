package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * 고정된 구형 범위 안의 대상을 매 틱 추적하는 장판 효과의 기반 형식이다.
 *
 * [radius]는 블록, [time]은 초 단위다. [time]이 `null`이면 [continueWhile]가 `false`를
 * 반환할 때까지 유지된다. 대상이 범위 안에 있는 동안 [onFlooringEntityHit]은 매 틱 호출된다.
 */
abstract class Flooring : EffectApiAccess {
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

    /** 시간 제한을 제거하고 [predicate]가 참인 동안 장판을 유지한다. */
    fun setContinueWhileIf(predicate: () -> Boolean) {
        this.continueWhile = predicate
        time = null
    }

    /** 장판이 활성화된 매 틱 호출된다. */
    open fun onFlooringContinue(location: Location) {}

    /** [hitEntityData]가 범위 안에 있는 매 틱 호출된다. */
    open fun onFlooringEntityHit(hitEntityData: EntityData, location: Location) {}

    /** 이전 틱까지 범위 안에 있던 [hitEntityData]가 벗어나면 한 번 호출된다. */
    open fun onFlooringEntityOut(hitEntityData: EntityData, location: Location) {}

    /** 지속 조건이 끝나거나 제한 시간이 지나면 한 번 호출된다. */
    open fun onFlooringEnd() {}

    /** 효과를 생성하고 생성자 플레이어의 정리 대상 작업으로 등록한다. */
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
                    if (continueWhile?.invoke() == false) {
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

                val isTraining = PlayerTagManager.isTraining(player)
                val targetCandidates = if (isTraining) {
                    trainingCandidates.clear()
                    trainingCandidateIds.clear()
                    game.playerDatas.forEach { data ->
                        val entityId = data.entity.uniqueId
                        if (trainingCandidateIds.add(entityId)) {
                            trainingCandidates.add(data)
                        }
                    }
                    for (livingEntity in player.world.livingEntities) {
                        if (livingEntity == player || livingEntity is Player) continue
                        val data = game.playerDatas.find { it.entity == livingEntity }
                            ?: if (livingEntity.isMannequin()) DummyEntityData(livingEntity, game)
                            else MobEntityData(livingEntity, game)
                        if (data !in game.playerDatas) game.playerDatas.add(data)
                        val entityId = livingEntity.uniqueId
                        if (trainingCandidateIds.add(entityId)) {
                            trainingCandidates.add(data)
                        }
                    }
                    trainingCandidates
                } else {
                    game.playerDatas
                }

                currentTargets.clear()
                for (targetData in targetCandidates) {
                    if (targetType == TargetType.Enemy && targetData == playerData) continue
                    if (!targetData.entityStatus.isSkillTargeting) continue
                    if (!HitboxUtil.intersectsSphere(targetData.entity.boundingBox, currentLocation.toVector(), radius)) continue
                    val isValidTarget = when (targetType) {
                        TargetType.Self -> targetData == playerData
                        TargetType.Enemy -> targetData !is PlayerData && isTraining ||
                            (targetData is PlayerData && playerData.isEnemyOf(targetData))
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
