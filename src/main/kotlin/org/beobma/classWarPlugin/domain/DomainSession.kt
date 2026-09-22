package org.beobma.classWarPlugin.domain

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.status.list.Distortion
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockState
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.Duration
import java.util.UUID
import kotlin.math.*

class DomainSession internal constructor(
    val scope: AbilityScope,
    val definition: DomainDefinition,
    val center: Location,
    private val target: PlayerData?,
) : AutoCloseable {
    val caster get() = scope.playerData
    val participants = linkedSetOf<UUID>()
    internal val distorted = linkedSetOf<UUID>()
    var isIntroducing = true
        private set
    private val timeline = DomainTimeline(System.nanoTime(), definition.subtitleDelayMillis, definition.titleDurationMillis)
    private val resources = ResourceScope()
    private val projectiles = DistortedProjectiles()
    private var ownerHandle: ResourceScope.Handle? = null
    private var task: BukkitTask? = null
    private var lock: AutoCloseable? = null
    private val distortionLeases = mutableMapOf<UUID, ResourceScope>()
    private val originalPositions = mutableMapOf<UUID, Location>()
    private val acceptedPositions = mutableMapOf<UUID, Location>()
    private val snapshots = linkedMapOf<Triple<Int, Int, Int>, BlockState>()
    private val interiorBlocks = mutableListOf<Location>()
    private val shell = mutableListOf<BlockDisplay>()
    private val melting = mutableListOf<Pair<BlockDisplay, Location>>()
    private val shellPoints = mutableListOf<Location>()
    private var transformed = false
    private var subtitle = false
    private var activeTicks = 0
    private var endTicks = -1
    private var closed = false
    internal var relocating: UUID? = null
    private var startedCombat = false
    private val effects = DomainEffects(this)
    var finishedNormally = false
        private set

    fun players(): List<Player> = participants.mapNotNull(Bukkit::getPlayer).filter { it.isOnline && !it.isDead }
    private val boundary = DomainBoundary(definition.radius.toDouble())
    fun contains(location: Location): Boolean = location.world == center.world &&
        boundary.contains(location.x - center.x, location.y - center.y, location.z - center.z)
    internal fun containsPlayer(id: UUID) = Bukkit.getPlayer(id)?.let { contains(it.location) } == true
    internal fun crosses(from: Location, to: Location): Boolean =
        from.world == center.world && to.world == center.world && boundary.crosses(
            from.x - center.x, from.y - center.y, from.z - center.z,
            to.x - center.x, to.y - center.y, to.z - center.z)

    internal fun start() {
        participants += caster.uniqueId
        if (target != null) participants += target.uniqueId else participants += center.world.players.filter { contains(it.location) && !it.isDead && it.gameMode != GameMode.SPECTATOR }.map { it.uniqueId }
        players().forEach { originalPositions[it.uniqueId] = it.location.clone(); acceptedPositions[it.uniqueId] = it.location.clone() }
        lock = caster.entityStatus.controlLocks.acquire(Control.MOVE, Control.ATTACK, Control.SKILL)
        resources.own { lock?.close() }
        val border = center.world.worldBorder
        val borderSize = border.size
        val borderCenter = border.center.clone()
        border.changeSize(borderSize, 0L)
        val required = 2 * (max(abs(center.x - borderCenter.x), abs(center.z - borderCenter.z)) + definition.radius + 3)
        border.size = max(borderSize, required).coerceAtMost(border.maxSize)
        resources.own { border.center = borderCenter; border.changeSize(borderSize, 0L) }
        val radius = definition.radius
        for (y in 0..radius) for (x in -radius..radius) for (z in -radius..radius) {
            val distance = sqrt((x * x + y * y + z * z).toDouble())
            if (distance >= radius - 0.8 && distance <= radius + 0.3) shellPoints += center.clone().add(x - 0.5, y.toDouble(), z - 0.5)
        }
        updateDistortion()
        projectiles.tick(center.world, distorted)
        enforceBoundary()
        ownerHandle = scope.resources.own { close() }
        effects.start()
        task = Bukkit.getScheduler().runTaskTimer(ClassWarPlugin.instance, Runnable {
            try { tick() } catch (error: Throwable) { close(); ClassWarPlugin.instance.logger.warning("영역 처리 실패: ${error.message}") }
        }, 1L, 1L)
    }

    private fun updateDistortion() {
        scope.game.playerDatas.filterIsInstance<PlayerData>().filter {
            it.player.isOnline && !it.entityStatus.isDead && !it.player.isDead && it.player.gameMode != GameMode.SPECTATOR && contains(it.player.location)
        }.forEach { data ->
            if (distorted.add(data.uniqueId)) {
                val effects = ResourceScope().also { distortionLeases[data.uniqueId] = it }
                val attackSpeed = data.attributeEffects.multiply(scope, Attribute.ATTACK_SPEED, 0.001)
                effects.own { attackSpeed.close() }
                val movementSpeed = data.attributeEffects.multiply(scope, Attribute.MOVEMENT_SPEED, 0.05)
                effects.own { movementSpeed.close() }
                val status = data.addStatus(Distortion(), caster)
                effects.own { status.cleanupFromManager() }
                data.player.resetCooldown()
            }
        }
    }

    private fun tick() {
        if (closed) return
        if (scope.isClosed || !scope.isActive || scope.suspended || !caster.player.isOnline || caster.entityStatus.isDead || caster.player.isDead) { close(); return }
        val now = System.nanoTime()
        enforceBoundary()
        if (isIntroducing) {
            updateDistortion()
            projectiles.tick(center.world, distorted)
            caster.player.velocity = Vector()
            val progress = (timeline.elapsedMillis(now) / 3000.0).coerceIn(0.0, 1.0)
            if (!scope.game.isPaused) effects.formation(progress)
            val iterator = shellPoints.iterator()
            while (iterator.hasNext()) {
                val point = iterator.next()
                val wave = sin(point.x * 1.7 + point.z * 1.3) * 0.7
                if (progress == 1.0 || point.y - center.y + wave <= progress * (definition.radius + 1)) {
                    shell += display(point, Material.BLACK_CONCRETE)
                    iterator.remove()
                }
            }
            if (!transformed && timeline.castComplete(now)) {
                transformInterior()
                transformed = true
                timeline.beginTitle(System.nanoTime())
                showTitle(false)
                // Always give the title an observable frame, even after a long server stall.
                return
            }
            if (transformed && !subtitle && timeline.subtitleVisible(now)) { subtitle = true; showTitle(true); return }
            if (transformed && subtitle && timeline.fightStarted(now) && !scope.game.isPaused) {
                releaseIntroduction()
                startedCombat = true
                effects.activate()
                AbilityExecution.with(scope) { definition.onStart(this) }
            }
            return
        }
        if (scope.game.isPaused) return
        if (endTicks >= 0) { dissolve(); return }
        AbilityExecution.with(scope) { definition.onTick(this) }
        if (closed) return
        effects.sustain(activeTicks)
        if (++activeTicks >= definition.durationTicks) {
            endTicks = 0
            effects.beginDissolve()
            interiorBlocks.forEach { location ->
                if (!location.block.type.isAir) melting += display(location, location.block.type) to location.clone()
                location.block.setType(Material.AIR, false)
            }
        }
    }

    private fun showTitle(withSubtitle: Boolean) {
        effects.reveal(withSubtitle)
        val stay = Duration.ofMillis(definition.titleDurationMillis + 1000)
        players().forEach { it.showTitle(Title.title(Component.text("「영역전개」", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
            Component.text(if (withSubtitle) "「${definition.name}」" else "", net.kyori.adventure.text.format.NamedTextColor.GOLD),
            Title.Times.times(Duration.ZERO, stay, Duration.ZERO))) }
    }

    private fun releaseIntroduction() {
        if (!isIntroducing) return
        isIntroducing = false
        lock?.close(); lock = null
        distortionLeases.values.forEach { it.close() }; distortionLeases.clear()
        distorted.forEach { Bukkit.getPlayer(it)?.resetCooldown() }
        distorted.clear()
        projectiles.close()
        players().forEach { it.clearTitle() }
    }

    internal fun removePlayer(id: UUID) {
        if (id !in participants && id !in distorted) return
        if (id == caster.uniqueId) { close(); return }
        participants.remove(id)
        acceptedPositions.remove(id)
        originalPositions.remove(id)
        distorted.remove(id)
        distortionLeases.remove(id)?.close()
        Bukkit.getPlayer(id)?.let { it.clearTitle(); it.resetCooldown() }
    }

    private fun transformInterior() {
        val r = definition.radius
        val plan = definition.interior(r)
        require(plan.size <= 8192 && plan.all { it.material.isBlock && it.y in 0 until r &&
            it.x * it.x + it.y * it.y + it.z * it.z < (r - 1) * (r - 1) }) { "Interior exceeds domain bounds" }
        for (x in -r..r) for (z in -r..r) {
            if (x * x + z * z >= (r - 1) * (r - 1)) continue
            change(x, -1, z, definition.floor)
            for (y in 0 until r) if (x * x + y * y + z * z < (r - 1) * (r - 1)) change(x, y, z, Material.AIR)
        }
        plan.forEach { block ->
            val location = center.clone().add(block.x.toDouble(), block.y.toDouble(), block.z.toDouble())
            // Keep standing players' feet and heads unobstructed.
            if (players().none { abs(it.location.x - location.x) < 1.5 && abs(it.location.z - location.z) < 1.5 &&
                    location.y >= it.location.y - 0.5 && location.y <= it.location.y + 2 }) {
                change(block.x, block.y, block.z, block.material)
                interiorBlocks += location.block.location
            }
        }
    }

    private fun change(x: Int, y: Int, z: Int, material: Material) {
        val block = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
        if (block.type == material) return
        snapshots.putIfAbsent(Triple(block.x, block.y, block.z), block.state)
        block.setType(material, false)
    }
    private fun display(location: Location, material: Material): BlockDisplay = center.world.spawn(location, BlockDisplay::class.java) {
        it.block = material.createBlockData(); it.isPersistent = false
        it.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(1.02f), Quaternionf())
    }
    private fun dissolve() {
        endTicks++
        effects.dissolve(endTicks)
        if (endTicks <= 20) {
            melting.forEach { (display, original) ->
                display.teleport(original.clone().apply { y -= (original.y - center.y + 2) * endTicks / 20.0 })
            }
        } else {
            melting.forEach { it.first.remove() }; melting.clear()
            val height = definition.radius * (1.0 - (endTicks - 20) / 20.0)
            shell.filter { it.location.y - center.y >= height }.forEach { it.remove() }
            shell.removeAll { !it.isValid }
        }
        if (endTicks >= 40) { finishedNormally = true; close() }
    }

    internal fun relocate(player: Player, location: Location) {
        relocating = player.uniqueId
        try { player.teleport(location); player.velocity = Vector() } finally { relocating = null }
    }
    private fun enforceBoundary() {
        center.world.players.filter { it.uniqueId !in participants && contains(it.location) }.forEach { player ->
            val direction = player.location.toVector().subtract(center.toVector()).setY(0)
            if (direction.lengthSquared() < 0.01) direction.x = 1.0
            val outside = center.clone().add(direction.normalize().multiply(definition.radius + 3.0))
            outside.y = center.world.getHighestBlockYAt(outside).toDouble() + 1
            outside.yaw = player.location.yaw; outside.pitch = player.location.pitch
            relocate(player, outside)
        }
        players().forEach { player ->
            if (!contains(player.location)) acceptedPositions[player.uniqueId]?.let { relocate(player, it) }
            else acceptedPositions[player.uniqueId] = player.location.clone()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Cleanup stages are independent: one failed teleport or block update must not leak the border/locks.
        val cleanup = ResourceScope()
        cleanup.own { if (startedCombat) AbilityExecution.with(scope) { definition.onEnd(this) } }
        cleanup.own { DomainManager.sessions.remove(this) }
        cleanup.own { resources.close() }
        cleanup.own {
            players().forEach { player ->
                if (!player.location.block.isPassable || !player.location.clone().add(0.0, 1.0, 0.0).block.isPassable)
                    originalPositions[player.uniqueId]?.let { relocate(player, it) }
            }
        }
        snapshots.values.forEach { state -> cleanup.own { state.update(true, false) } }
        shell.forEach { display -> cleanup.own { display.remove() } }
        melting.forEach { (display, _) -> cleanup.own { display.remove() } }
        cleanup.own { releaseIntroduction() }
        cleanup.own { task?.cancel(); ownerHandle?.forget() }
        runCatching { cleanup.close() }.onFailure {
            ClassWarPlugin.instance.logger.log(java.util.logging.Level.SEVERE, "영역 복원 중 오류", it)
        }
    }
}
