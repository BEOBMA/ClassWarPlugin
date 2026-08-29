package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusDurationMode
import org.beobma.classWarPlugin.status.handler.StatusOnHitHandler
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.beobma.classWarPlugin.status.handler.StatusWhenHitHandler
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

class Freezing : StatusAbnormality(), StatusOnHitHandler, StatusWhenHitHandler, StatusPlayerMoveHandler {
    override val name: String
        get() = Keyword.Freezing.string
    override val description: List<String>
        get() = listOf(
            Keyword.Freezing.requireDescription(),
            "",
            "<gray>수치 없음",
            "<gray>지속시간 연장",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var power: Int = 1
    override var maxPower: Int? = 1
    override var duration: Int? = null
    override val durationMode: StatusDurationMode = StatusDurationMode.Extend
    override val showMaxPower: Boolean = false
    override val showPower: Boolean = false

    private var iceDisplay: BlockDisplay? = null
    private var displayTask: BukkitTask? = null

    override fun onDurationChanged() {
        ensureIceDisplay()
        super.onDurationChanged()
    }

    override fun onPowerChanged() {
        ensureIceDisplay()
        super.onPowerChanged()
    }

    override fun onAttackHit(context: DamageContext) {
        context.isCancelled = true
    }

    override fun whenAttackHit(context: DamageContext) {
        context.target.damage(context.damage / 2, DamageType.StatusAbnormality, casterData)
        this.remove()
    }

    override fun onPlayerMove(
        event: PlayerMoveEvent,
        playerData: PlayerData
    ) {
        event.isCancelled = true
    }

    override fun onRemoveStatusAbnormality() {
        displayTask?.cancel()
        displayTask = null
        iceDisplay?.remove()
        iceDisplay = null
        super.onRemoveStatusAbnormality()
    }

    private fun ensureIceDisplay() {
        if (iceDisplay != null) return
        val living = entity as? LivingEntity ?: return
        val box = living.boundingBox
        val display = living.world.spawn(box.min.toLocation(living.world), BlockDisplay::class.java)
        display.block = Material.BLUE_ICE.createBlockData()
        display.isPersistent = false
        TemporaryDisplayManager.mark(display, casterData.uniqueId)
        display.isGlowing = true
        display.glowColorOverride = org.bukkit.Color.AQUA
        display.transformation = Transformation(
            Vector3f(-0.1f, -0.1f, -0.1f),
            Quaternionf(),
            Vector3f(
                (box.widthX + 0.2).toFloat(),
                (box.height + 0.2).toFloat(),
                (box.widthZ + 0.2).toFloat(),
            ),
            Quaternionf(),
        )
        iceDisplay = display

        val task = object : BukkitRunnable() {
            override fun run() {
                val current = iceDisplay
                if (current == null || !living.isValid || living.isDead || power <= 0) {
                    current?.remove()
                    cancel()
                    return
                }
                val currentBox = living.boundingBox
                current.teleport(currentBox.min.toLocation(living.world))
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L)
        displayTask = task
        entityData.bukkitTasks.add(task)
        game.tasks.add(task)
    }
}
