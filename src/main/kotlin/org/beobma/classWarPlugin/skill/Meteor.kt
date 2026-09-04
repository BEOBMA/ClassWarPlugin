package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.ability.AbilityScope
import org.beobma.classWarPlugin.ability.AbilityExecution

import org.beobma.classWarPlugin.ability.Targeting

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.TargetType.Self
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask

/**
 * 한 틱마다 아래쪽으로 이동하며 블록과 엔티티 충돌을 검사하는 낙하체의 기반 형식이다.
 * [speed]는 틱당 블록, [time]은 초 단위다. 충돌하거나 지속 조건이 끝나면 [onMeteorEnd]가
 * 정확히 한 번 호출된다.
 */
abstract class Meteor(

) : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected val player: Player get() = playerData.player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var abilityScope: AbilityScope
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
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
        this.abilityScope = checkNotNull(AbilityExecution.current)
    }

    /** 충돌하지 않은 각 이동 틱에 호출된다. */
    open fun onMeteorMove(location: Location) {}

    /** 유효한 대상과 충돌했을 때 종료 전에 호출된다. */
    open fun onMeteorEntityHit(hitEntityData: EntityData, location: Location) {}

    /** [isWallHit]이 켜진 상태에서 고체 블록과 충돌했을 때 호출된다. */
    open fun onMeteorBlockHit(hitBlock: Block, location: Location) {}

    /** 낙하체가 어떤 이유로든 종료될 때 한 번 호출된다. */
    open fun onMeteorEnd(location: Location) {}

    /** 낙하체를 생성하고 생성자 플레이어의 정리 대상 작업으로 등록한다. */
    fun spawnMeteor(playerData: PlayerData) {
        inject(playerData)
        val currentLocation = location.clone()
        val time = time
        var ticks = 0

        val task = object : BukkitRunnable(abilityScope) {
            var stopped = false

            private fun stop() = cancel()

            override fun onCancel() {
                if (stopped) return
                stopped = true
                onMeteorEnd(currentLocation)
            }

            override fun run() {
                if (time == null) {
                    if (continueWhile?.invoke() == false) {
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

                val collidedEntityData = Targeting.select(playerData, targetType, currentLocation.world,
                    includeSelf = targetType == Self).firstOrNull {
                    it.entity.boundingBox.contains(currentLocation.toVector())
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
