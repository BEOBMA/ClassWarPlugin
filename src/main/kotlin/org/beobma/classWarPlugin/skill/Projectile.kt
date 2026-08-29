package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.AttackableObjectManager
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.gameClass.list.PortalGun
import org.beobma.classWarPlugin.util.TargetType.All
import org.beobma.classWarPlugin.util.TargetType.Enemy
import org.beobma.classWarPlugin.util.TargetType.Self
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class Projectile : EffectApiAccess {
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

    open val itemDisplayItem: ItemStack? = null

    private var durationTask: BukkitTask? = null
    private var itemDisplay: ItemDisplay? = null

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

    open fun onItemDisplaySpawn(display: ItemDisplay, location: Location) {}

    open fun onItemDisplayMove(display: ItemDisplay, location: Location, speed: Double, tick: Int) {}

    open fun interpolateSpeed(previousSpeed: Double, tick: Int): Double {
        if (speed <= 0.0) return speed
        val acceleration = abs(speed) * 0.2
        return (previousSpeed + acceleration).coerceAtMost(speed)
    }

    open fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {}

    open fun onProjectileBlockHit(hitBlock: Block, location: Location) {}

    open fun onProjectileEnd(location: Location) {}

    fun spawnProjectile(playerData: PlayerData) {
        inject(playerData)
        val game = game
        val durationLimitTicks = time?.let { seconds ->
            (seconds * 20.0 * ClassBalanceManager.rangeMultiplier(playerData)).roundToInt().coerceAtLeast(1)
        }
        if (isFlatMove) location.pitch = 0F

        val direction = location.direction.normalize()
        val currentLocation = location.clone()
        var elapsedTicks = 0
        var durationTicks = 0
        var currentSpeed = 0.0
        val trainingCandidates: MutableList<EntityData> = ArrayList()
        val trainingCandidateIds: MutableSet<UUID> = HashSet()

        spawnItemDisplay(currentLocation)

        val task = object : BukkitRunnable() {
            var stopped = false

            private fun stop() {
                if (stopped) return
                stopped = true
                removeItemDisplay()
                onProjectileEnd(currentLocation)
                cancel()
            }

            override fun run() {
                if (durationLimitTicks == null) {
                    if (continueWhile != null && !continueWhile!!.invoke()) {
                        stop()
                        return
                    }
                } else {
                    if (durationTicks++ >= durationLimitTicks) {
                        stop()
                        return
                    }
                }

                if (isWallHit && currentLocation.block.type.isSolid) {
                    onProjectileBlockHit(currentLocation.block, currentLocation)
                    stop()
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
                    var collidedEntityData: EntityData? = null
                    for (targetData in targetCandidates) {
                        if (targetData == playerData || !targetData.entityStatus.isSkillTargeting) continue
                        val bb = targetData.entity.boundingBox.expand(xSize, ySize, zSize)
                        if (!bb.contains(currentLocation.x, currentLocation.y, currentLocation.z)) continue
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
                        onProjectileEntityHit(collidedEntityData, currentLocation)
                        if (isPlayerHitRemove) {
                            stop()
                            return
                        }
                    }
                }

                currentSpeed = interpolateSpeed(currentSpeed, elapsedTicks)
                PortalGun.teleportCustomProjectile(player.uniqueId, currentLocation, direction, currentSpeed)
                val previousLocation = currentLocation.clone()
                val nextLocation = previousLocation.clone().add(direction.clone().multiply(currentSpeed))
                val projectileExpansion = maxOf(xSize, ySize, zSize).coerceAtLeast(0.0)
                if (AttackableObjectManager.hitProjectileSegment(
                        player.uniqueId,
                        previousLocation,
                        nextLocation,
                        projectileExpansion,
                    )
                ) {
                    stop()
                    return
                }
                val speedRatio = if (speed == 0.0) 1.0 else (currentSpeed / speed).coerceIn(0.0, 1.0)
                val interpolatedLocation = lerpLocation(previousLocation, nextLocation, speedRatio)
                updateItemDisplay(interpolatedLocation, currentSpeed, elapsedTicks)
                onProjectileMove(interpolatedLocation)
                currentLocation.set(nextLocation.x, nextLocation.y, nextLocation.z)
                elapsedTicks++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        durationTask = playerData.trackTask(task)
    }

    private fun spawnItemDisplay(startLocation: Location) {
        val item = itemDisplayItem?.clone() ?: return
        val display = startLocation.world.spawn(startLocation, ItemDisplay::class.java)
        display.setItemStack(item)
        TemporaryDisplayManager.mark(display, player.uniqueId)
        itemDisplay = display
        onItemDisplaySpawn(display, startLocation)
    }

    private fun updateItemDisplay(location: Location, currentSpeed: Double, tick: Int) {
        val display = itemDisplay ?: return
        display.teleport(location)
        onItemDisplayMove(display, location, currentSpeed, tick)
    }

    private fun removeItemDisplay() {
        itemDisplay?.remove()
        itemDisplay = null
    }

    private fun lerpLocation(start: Location, end: Location, t: Double): Location {
        val clamped = t.coerceIn(0.0, 1.0)
        val x = start.x + (end.x - start.x) * clamped
        val y = start.y + (end.y - start.y) * clamped
        val z = start.z + (end.z - start.z) * clamped
        return Location(start.world, x, y, z, start.yaw, start.pitch)
    }
}
