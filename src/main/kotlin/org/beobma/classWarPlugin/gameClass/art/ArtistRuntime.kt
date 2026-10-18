package org.beobma.classWarPlugin.gameClass.art

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.entity.BlockDisplay
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.abs

internal object PaintRules {
    const val MAX_BLOCKS = 3_072

    fun damage(stage: Int) = (0.01 + stage.coerceIn(0, 20) * (3.99 / 20)).coerceAtMost(4.0)
}

internal object PaintMovement {
    const val SPEED = 0.5

    /** The fixed camera faces south (yaw 0): screen-left is +X, screen-up is +Z. */
    fun step(forward: Boolean, backward: Boolean, left: Boolean, right: Boolean): Vector {
        val x = (if (left) 1.0 else 0.0) - (if (right) 1.0 else 0.0)
        val z = (if (forward) 1.0 else 0.0) - (if (backward) 1.0 else 0.0)
        val direction = Vector(x, 0.0, z)
        return if (direction.lengthSquared() == 0.0) direction else direction.normalize().multiply(SPEED)
    }
}

/** A thin display coating preserves containers, block states and other temporary terrain. */
class ArtistRuntime(private val scope: AbilityScope) : EffectApiAccess {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private val game get() = scope.game
    private data class Cell(val at: Location, val original: org.bukkit.block.data.BlockData, val display: BlockDisplay,
                            var expires: Long, var stage: Int = 0)
    private val cells = linkedMapOf<Location, Cell>()
    private val lastDamage = mutableMapOf<UUID, Long>()
    private var flight: ResourceScope? = null
    private var camera: Location? = null
    private val palette = listOf(Material.CYAN_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
        Material.PINK_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.PURPLE_CONCRETE)

    fun start() {
        active += this
        scope.resources.own {
            flight?.close(); flight = null; camera = null
            cells.values.forEach { it.display.remove() }; cells.clear(); lastDamage.clear(); active -= this
        }
        val speed = owner.attributeEffects.multiply(scope, Attribute.MOVEMENT_SPEED, 1.0)
        owner.trackTask(object : AbilityRunnable(scope) {
            override fun run() {
                cells.values.toList().filter { game.combatTick >= it.expires || !it.display.isValid || it.at.block.blockData != it.original }
                    .forEach { cells.remove(it.at); it.display.remove() }
                speed.setMultiplier(if (touches(player.boundingBox, player.world).isNotEmpty()) 1.2 else 1.0)
                Targeting.select(owner, TargetType.Enemy).forEach { target ->
                    val touching = touches(target.entity.boundingBox, target.entity.world)
                    if (touching.isEmpty() || game.combatTick - (lastDamage[target.entity.uniqueId] ?: -100L) < 20) return@forEach
                    lastDamage[target.entity.uniqueId] = game.combatTick
                    target.damage(PaintRules.damage(touching.maxOf { it.stage }), DamageType.Normal, owner,
                        damagePath = org.beobma.classWarPlugin.damage.DamagePath.STATUS_EFFECT)
                    particles.spawn(target.entity.location, Particle.DUST, Particle.DustOptions(Color.fromRGB(245, 60, 100), 0.8f),
                        org.beobma.classWarPlugin.effect.ParticleOptions.spread(5, 0.3, 0.0))
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 2L))
    }

    private fun touches(box: org.bukkit.util.BoundingBox, world: World) = cells.values.filter {
        it.at.world == world && box.clone().expand(0.06).overlaps(it.at.block.boundingBox)
    }

    private fun paint(block: Block, ticks: Int) {
        if (!block.type.isSolid || block.type == Material.BARRIER || block.type == Material.LIGHT) return
        if (listOf(org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST).none { block.getRelative(it).isPassable }) return
        val at = block.location
        cells[at]?.let { it.expires = maxOf(it.expires, game.combatTick + ticks); return }
        // Bound display load even with prolonged or repeated painting.
        if (cells.size >= PaintRules.MAX_BLOCKS) cells.entries.first().let { it.value.display.remove(); cells.remove(it.key) }
        val box = block.boundingBox
        val display = at.world.spawn(at, BlockDisplay::class.java).apply {
            this.block = palette.random().createBlockData()
            isPersistent = false
            brightness = org.bukkit.entity.Display.Brightness(15, 15)
            shadowRadius = 0f
            shadowStrength = 0f
            transformation = Transformation(Vector3f((box.minX - at.x - 0.003).toFloat(), (box.minY - at.y - 0.003).toFloat(),
                (box.minZ - at.z - 0.003).toFloat()), Quaternionf(),
                Vector3f((box.widthX + 0.006).toFloat(), (box.height + 0.006).toFloat(), (box.widthZ + 0.006).toFloat()), Quaternionf())
        }
        TemporaryDisplayManager.mark(display, owner.uniqueId)
        cells[at] = Cell(at, block.blockData.clone(), display, game.combatTick + ticks)
    }

    fun paintAround(at: Location, radius: Int, ticks: Int) {
        for (x in -radius..radius) for (y in -radius..radius) for (z in -radius..radius) {
            if (x*x+y*y+z*z <= radius*radius) paint(at.block.getRelative(x,y,z), ticks)
        }
        particles.spawn(at, Particle.HAPPY_VILLAGER, count = 16, spread = 1.0, speed = 0.03)
        sounds.play(at, Sound.ITEM_BRUSH_BRUSHING_GENERIC, volume = 0.65f, pitch = 1.3f)
    }

    private fun strengthen(target: org.beobma.classWarPlugin.entity.EntityData) {
        if (target.game !== game || !Targeting.isEnemy(owner, target)) return
        val touched = touches(target.entity.boundingBox, target.entity.world)
        if (touched.isEmpty()) return
        val center = target.entity.boundingBox.center
        cells.values.filter { it.at.world == target.entity.world && it.at.toVector().distanceSquared(center) <= 16.0 }.forEach {
            it.stage = (it.stage + 1).coerceAtMost(20)
            it.display.block = when {
                it.stage >= 14 -> Material.RED_CONCRETE
                it.stage >= 7 -> Material.ORANGE_CONCRETE
                else -> palette[(it.stage + abs(it.at.blockX + it.at.blockZ)) % palette.size]
            }.createBlockData()
        }
    }

    fun lockView(event: PlayerMoveEvent) {
        val at = camera ?: return
        event.to = at.clone()
    }

    fun overpaint(duration: Int): Boolean {
        if (flight != null) return false
        val session = ResourceScope(); flight = session
        val origin = player.location.clone()
        val gravity = player.hasGravity()
        val controls = owner.entityStatus.controlLocks.acquire(Control.MOVE, Control.ATTACK, Control.SKILL)
        val disability = owner.addStatus(object : StatusAbnormality() {
            override val name = Keyword.Disability.string
            override val description = listOf(Keyword.Disability.requireDescription())
            override val canRemove = false
            override val showPower = false
            override var power = 1
        }, owner).apply { this.duration = (duration + 19) / 20; onDurationChanged() }
        val stealth = owner.addStatus(Stealth(), owner).apply { updatePower(1); updateDuration((duration + 19) / 20) }
        camera = origin.clone().apply { y = world.getHighestBlockYAt(this) + 20.0; yaw = 0f; pitch = 90f }
        session.own {
            controls.close(); disability.cleanupFromManager(); stealth.cleanupFromManager()
            camera = null; player.setGravity(gravity); player.velocity = Vector(); player.fallDistance = 0f
            owner.returnFromAbility(origin); flight = null
        }
        player.setGravity(false)
        player.teleport(camera!!)
        sounds.play(origin, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 0.8f, pitch = 1.4f)
        owner.trackTask(object : AbilityRunnable(scope, cancelOnDisconnect = true) {
            private var tick = 0
            override fun onCancel() { session.close() }
            override fun run() {
                if (tick >= duration || session.isClosed || player.isDead || player.world != origin.world) { cancel(); return }
                val at = camera ?: run { cancel(); return }
                // Poll held input each tick: holding keeps moving, releasing stops immediately.
                // Native movement remains locked; only this controlled horizontal motion is allowed.
                val input = player.currentInput
                val next = at.clone().add(PaintMovement.step(input.isForward, input.isBackward, input.isLeft, input.isRight))
                if (next.world.worldBorder.isInside(next)) { at.x = next.x; at.z = next.z }
                at.y = at.world.getHighestBlockYAt(at) + 20.0
                player.teleport(at); player.velocity = Vector(); player.fallDistance = 0f
                if (tick % 4 == 0) {
                    for (x in -1..1) for (z in -1..1) {
                        val highest = at.world.getHighestBlockYAt(at.blockX+x, at.blockZ+z)
                        for (y in at.world.minHeight..highest) paint(at.world.getBlockAt(at.blockX+x,y,at.blockZ+z), 300)
                    }
                    val floor = at.clone().apply { y = world.getHighestBlockYAt(this) + 1.05 }
                    particles.spawn(floor, Particle.HAPPY_VILLAGER, count = 15, spread = 1.2, speed = 0.02)
                }
                if (tick % 20 == 0) {
                    val column = org.bukkit.util.BoundingBox(at.blockX-1.0, at.world.minHeight.toDouble(), at.blockZ-1.0,
                        at.blockX+2.0, at.y, at.blockZ+2.0)
                    Targeting.select(owner, TargetType.Enemy, at.world).filter { it.entity.boundingBox.overlaps(column) }
                        .forEach { it.damage(2.0, DamageType.Normal, owner, damagePath = org.beobma.classWarPlugin.damage.DamagePath.STATUS_EFFECT) }
                    sounds.play(at, Sound.ITEM_BRUSH_BRUSHING_GENERIC, volume = 0.45f, pitch = 1.6f)
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
        return true
    }

    companion object {
        private val active = mutableSetOf<ArtistRuntime>()
        fun isPainting(id: UUID) = active.any { it.owner.uniqueId == id && it.camera != null }
        fun onDamage(context: DamageContext) = active.toList().forEach { runtime ->
            if (!runtime.scope.isClosed) AbilityExecution.with(runtime.scope) { runtime.strengthen(context.target) }
        }
        fun onEnvironmentalDamage(entity: org.bukkit.entity.Entity) = active.toList().forEach { runtime ->
            val target = runtime.game.playerDatas.firstOrNull { it.entity.uniqueId == entity.uniqueId }
            if (target != null && !runtime.scope.isClosed) AbilityExecution.with(runtime.scope) { runtime.strengthen(target) }
        }
    }
}
