package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType.All
import org.beobma.classWarPlugin.util.TargetType.Enemy
import org.beobma.classWarPlugin.util.TargetType.Self
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

abstract class Meteor(

) : EffectApiAccess {
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
    open fun onMeteorEntityHit(hitEntityData: EntityData, location: Location) {}
    open fun onMeteorBlockHit(hitBlock: Block, location: Location) {}
    open fun onMeteorEnd(location: Location) {}

    fun spawnMeteor(playerData: PlayerData) {
        inject(playerData)
        val currentLocation = location.clone()
        val time = time
        val isTraining = PlayerTagManager.hasTag(player, "isTraining")
        var ticks = 0

        val task = object : BukkitRunnable() {
            var stopped = false

            private fun stop() {
                if (stopped) return
                stopped = true
                onMeteorEnd(currentLocation)
                cancel()
            }

            override fun run() {
                if (isTraining) {
                    player.world.livingEntities.filter { it != player && it !is Player }.forEach { livingEntity ->
                        if (game.playerDatas.any { it.entity == livingEntity }) return@forEach
                        game.playerDatas.add(
                            if (livingEntity.isMannequin()) DummyEntityData(livingEntity, game)
                            else MobEntityData(livingEntity, game)
                        )
                    }
                }
                if (time == null) {
                    if (continueWhile != null && !continueWhile!!.invoke()) {
                        stop()
                        return
                    }
                } else {
                    if (ticks++ >= time * 20) {
                        stop()
                        return
                    }
                }

                if (isWallHit && currentLocation.block.type.isSolid) {
                    onMeteorBlockHit(currentLocation.block, currentLocation)
                    stop()
                    return
                }

                var collidedEntityData: EntityData? = null
                for (targetData in game.playerDatas) {
                    if (targetData == playerData || !targetData.entityStatus.isSkillTargeting) continue
                    if (!targetData.entity.boundingBox.contains(currentLocation.toVector())) continue
                    val isValidTarget = when (targetType) {
                        Self -> targetData == playerData
                        Enemy -> targetData !is PlayerData && isTraining ||
                            (targetData is PlayerData && playerData.isEnemyOf(targetData))
                        All -> true
                    }
                    if (isValidTarget) {
                        collidedEntityData = targetData
                        break
                    }
                }

                if (collidedEntityData != null) {
                    onMeteorEntityHit(collidedEntityData, currentLocation)
                    stop()
                    return
                }

                onMeteorMove(currentLocation)
                currentLocation.y -= speed
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        durationTask = playerData.trackTask(task)
    }
}
