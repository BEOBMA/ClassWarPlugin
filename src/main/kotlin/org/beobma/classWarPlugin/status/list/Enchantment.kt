package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.handler.StatusOnHitHandler
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class Enchantment : StatusAbnormality(), StatusOnHitHandler, StatusPlayerMoveHandler {
    override val name = Keyword.Enchantment.string
    override val description = listOf(Keyword.Enchantment.description ?: "")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null
    private var pullTask: BukkitTask? = null

    override fun onAttackHit(context: DamageContext) {
        context.isCancelled = true
    }

    override fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData) {
        val caster = casterData.player
        if (!caster.isOnline || caster.world != event.player.world) {
            event.to = event.from
            return
        }
        val direction = caster.boundingBox.center.subtract(event.player.boundingBox.center)
        val lookDirection = caster.eyeLocation.toVector().subtract(event.player.eyeLocation.toVector())
        val look = event.to.clone().setDirection(lookDirection)
        if (direction.lengthSquared() < 0.36) {
            event.to = event.from.clone().apply { yaw = look.yaw; pitch = look.pitch }
            return
        }
        val step = direction.normalize().multiply(0.18)
        event.to = event.from.clone().add(step).apply { yaw = look.yaw; pitch = look.pitch }
    }

    override fun onDurationChanged() {
        ensurePullTask()
        super.onDurationChanged()
    }

    override fun onPowerChanged() {
        ensurePullTask()
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        pullTask?.cancel()
        pullTask = null
        super.onRemoveStatusAbnormality()
    }

    private fun ensurePullTask() {
        if (pullTask != null) return
        val living = entity as? LivingEntity ?: return
        pullTask = object : BukkitRunnable() {
            override fun run() {
                val caster = casterData.player
                if (power <= 0 || !living.isValid || living.isDead || !caster.isOnline || caster.world != living.world) {
                    cancel()
                    pullTask = null
                    return
                }
                val direction = caster.boundingBox.center.subtract(living.boundingBox.center)
                val lookDirection = caster.eyeLocation.toVector().subtract(living.eyeLocation.toVector())
                if (lookDirection.lengthSquared() > 1.0E-8) {
                    val look = living.location.clone().setDirection(lookDirection)
                    living.setRotation(look.yaw, look.pitch)
                }
                if (direction.lengthSquared() > 0.36) {
                    val pull = direction.normalize().multiply(0.24)
                    living.velocity = living.velocity.multiply(0.35).add(pull)
                }
                ParticleApi.spawn(
                    living.boundingBox.center.toLocation(living.world),
                    Particle.HEART,
                    ParticleOptions(2, 0.28, 0.35, 0.28, 0.015),
                )
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L).also {
            entityData.bukkitTasks.add(it)
            game.tasks.add(it)
        }
    }
}
