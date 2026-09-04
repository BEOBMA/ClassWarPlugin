package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ability.AbilityScope
import org.beobma.classWarPlugin.ability.AbilityExecution

import org.beobma.classWarPlugin.ability.Targeting

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.AttackableObjectManager
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.gameClass.list.PortalGun
import org.beobma.classWarPlugin.util.TargetType.Self
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 시선 방향으로 이동하며 블록·엔티티·공격 가능 오브젝트 충돌을 처리하는 투사체 기반 형식이다.
 *
 * [speed]는 틱당 블록, [time]은 초, [xSize]·[ySize]·[zSize]는 대상 히트박스 확장량이다.
 * 제한 시간은 클래스 사거리 배율에 따라 함께 조정된다. [itemDisplayItem]을 제공하면 투사체와
 * 수명이 같은 비영구 [ItemDisplay]가 자동으로 생성되고 제거된다.
 */
abstract class Projectile : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected val player: Player get() = playerData.player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var abilityScope: AbilityScope
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
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
        this.abilityScope = checkNotNull(AbilityExecution.current)
    }

    /** 시간 제한을 제거하고 [predicate]가 참인 동안 투사체를 유지한다. */
    fun setContinueWhileIf(predicate: () -> Boolean) {
        this.continueWhile = predicate
        time = null
    }

    open fun onProjectileMove(location: Location) {}

    open fun onItemDisplaySpawn(display: ItemDisplay, location: Location) {}

    open fun onItemDisplayMove(display: ItemDisplay, location: Location, speed: Double, tick: Int) {}

    /**
     * 다음 틱의 이동 속도를 계산한다.
     * 기본 구현은 목표 [speed]의 20%씩 가속하되 목표 속도를 넘지 않는다.
     */
    open fun interpolateSpeed(previousSpeed: Double, tick: Int): Double {
        if (speed <= 0.0) return speed
        val acceleration = abs(speed) * 0.2
        return (previousSpeed + acceleration).coerceAtMost(speed)
    }

    open fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {}

    open fun onProjectileBlockHit(hitBlock: Block, location: Location) {}

    open fun onProjectileEnd(location: Location) {}

    /** 투사체를 생성하고 생성자 플레이어의 정리 대상 작업으로 등록한다. */
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

        spawnItemDisplay(currentLocation)

        val task = object : BukkitRunnable(abilityScope) {
            var stopped = false

            private fun stop() = cancel()

            override fun onCancel() {
                if (stopped) return
                stopped = true
                removeItemDisplay()
                onProjectileEnd(currentLocation)
            }

            override fun run() {
                if (durationLimitTicks == null) {
                    if (continueWhile?.invoke() == false) {
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
                    val collidedEntityData = Targeting.select(playerData, targetType, currentLocation.world,
                        includeSelf = targetType == Self).firstOrNull { target ->
                        target.entity.boundingBox.expand(xSize, ySize, zSize)
                            .contains(currentLocation.x, currentLocation.y, currentLocation.z)
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
