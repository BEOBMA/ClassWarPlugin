package org.beobma.classWarPlugin.gameClass.creator

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.domain.*
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

/** Combat-clock timestamps: pausing the game cannot age creations or the chain combo. */
internal class ChainCombo {
    private val hits = mutableMapOf<UUID, ArrayDeque<Long>>()
    fun hit(id: UUID, tick: Long): Boolean {
        prune(tick)
        val window = hits.getOrPut(id) { ArrayDeque() }
        window.addLast(tick)
        return (window.size >= 5).also { if (it) hits.remove(id) }
    }
    fun prune(tick: Long) {
        hits.values.forEach { q -> while (q.isNotEmpty() && tick - q.first() > 100) q.removeFirst() }
        hits.entries.removeIf { it.value.isEmpty() }
    }
}

class CreationRuntime(private val scope: AbilityScope) {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private val game get() = scope.game
    private val creations = mutableListOf<Creation>()
    private val combo = ChainCombo()
    private val effects = CreationEffects(scope)
    private var domain: DomainSession? = null
    val isCreationSpaceActive: Boolean get() = domain != null
    private var infiniteManaDisplay: AutoCloseable? = null
    private var exhaustedUntil = 0L
    private data class Creation(val chain: Boolean, val display: Display, var position: Location,
        var direction: Vector, var flying: Boolean = true, var attached: Entity? = null,
        var offset: Vector = Vector(), var age: Int = 0, var destroyAt: Long? = null,
        val hit: MutableSet<UUID> = mutableSetOf(),
        val links: MutableList<BlockDisplay> = mutableListOf(), val anchor: Vector? = null,
        val impactDamage: Double = if (chain) 2.0 else 5.0,
        val spearModel: LightSpearModel? = null)

    fun start() {
        owner.getOrCreateStatus(owner) { Mana() }.updatePower(100)
        owner.addStatus(CreationStatus {
            val state = if (domain != null) "<light_purple>창조 공간</light_purple>"
                else if (game.combatTick < exhaustedUntil) "<red>권능 소진 ${(exhaustedUntil - game.combatTick + 19) / 20}s</red>" else "<gold>권능</gold>"
            "$state <gray>사슬 ${creations.count { it.chain }}/10 · 빛의 창 ${creations.count { !it.chain }}/3</gray>"
        }, owner).updatePower(1)
        scope.resources.own { clear(); domain = null; infiniteManaDisplay?.close(); infiniteManaDisplay = null }
        object : AbilityRunnable(scope) {
            override fun run() { creations.toList().forEach(::tick); combo.prune(game.combatTick); effects.tick() }
            override fun onCancel() { clear(); effects.clear() }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    fun recoverMana() {
        owner.getOrCreateStatus(owner) { Mana() }.increasePower(if (domain != null) 100 else if (game.combatTick < exhaustedUntil) 1 else 10)
    }

    private fun spend(amount: Int): Boolean {
        if (domain != null) return true
        val mana = owner.getOrCreateStatus(owner) { Mana() }
        if (mana.power < amount) { player.sendMessage("§c마나가 부족하다. ($amount 필요)"); return false }
        mana.decreasePower(amount)
        return true
    }

    private fun enemies(world: World = player.world) = Targeting.select(owner, TargetType.Enemy, world)
    private fun guaranteed(): EntityData? = domain?.let { area ->
        enemies().filter { area.contains(it.entity.location) }
            .minByOrNull { it.entity.location.distanceSquared(player.location) }
    }

    fun chain(): Boolean {
        val target = guaranteed()
        val ground = target?.entity?.boundingBox?.center?.toLocation(player.world)
            ?: player.world.rayTraceBlocks(player.eyeLocation, player.eyeLocation.direction, 20.0,
                FluidCollisionMode.NEVER, true)?.hitPosition?.toLocation(player.world)
        if (ground == null) { player.sendMessage("§c20칸 내의 블록을 바라보아야 한다."); return false }
        if (!spend(10)) return false
        val height = (player.world.maxHeight - 1.0 - ground.y).coerceIn(0.1, 24.0)
        val tilt = if (kotlin.random.Random.nextDouble() < 0.6) kotlin.random.Random.nextDouble(0.12, 0.28) else 0.0
        val angle = kotlin.random.Random.nextDouble(0.0, Math.PI * 2)
        val from = ground.clone().add(ChainVisuals.spawnOffset(height, tilt, angle))
        val display = from.world.spawn(from, BlockDisplay::class.java).apply {
            block = Material.IRON_CHAIN.createBlockData()
            transformation = Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(1f), Quaternionf())
        }
        add(Creation(true, display, from, Vector(0.0, -2.4, 0.0), attached = target?.entity,
            offset = ground.toVector(), anchor = from.toVector(), impactDamage = ChainVisuals.damage(isCreationSpaceActive)))
        effects.summon(from, true, ground.toVector().subtract(from.toVector()))
        effects.seal(ground.clone().add(0.0, 0.08, 0.0), 0.6)
        return true
    }

    fun spear(): Boolean {
        if (!spend(30)) return false
        val from = player.eyeLocation.clone()
        val target = guaranteed()
        val direction = target?.entity?.boundingBox?.center?.subtract(from.toVector())
            ?.takeIf { it.lengthSquared() > 1e-8 }?.normalize() ?: from.direction
        val display = from.world.spawn(from, ItemDisplay::class.java).apply {
            setItemStack(ItemStack(Material.GOLDEN_SWORD))
            brightness = Display.Brightness(15, 15)
        }
        DisplayOrientationUtil.alignSwordBladeVertically(display, direction, 0.9f)
        val model = LightSpearModel(from, owner.uniqueId).also { it.move(from, direction) }
        add(Creation(false, display, from, direction.clone().multiply(2.7), attached = target?.entity, spearModel = model))
        effects.summon(from.clone().add(direction), false, direction)
        SoundApi.play(from, Sound.ITEM_TRIDENT_THROW, volume = 0.8f, pitch = 1.5f)
        return true
    }

    private fun add(creation: Creation) {
        val cap = if (creation.chain) 10 else 3
        creations.firstOrNull { it.chain == creation.chain }
            ?.takeIf { creations.count { it.chain == creation.chain } >= cap }?.let(::remove)
        TemporaryDisplayManager.mark(creation.display, owner.uniqueId)
        creation.display.setGravity(false)
        creation.display.teleportDuration = 1
        if (domain != null) creation.destroyAt = game.combatTick + 20
        creations += creation
        owner.updateStatusActionBar()
    }

    private fun tick(c: Creation) {
        if (!c.display.isValid) { remove(c); return }
        if (c.destroyAt?.let { game.combatTick >= it } == true) { destroy(c); return }
        if (!c.flying) {
            c.attached?.takeIf { it.isValid && !it.isDead && it.world == c.position.world }?.let {
                c.position = it.location.add(c.offset); c.display.teleport(c.position.clone().apply { yaw = 0f; pitch = 0f })
            }
            c.spearModel?.move(c.position, c.direction)
            if (game.combatTick % 12 == 0L) {
                if (c.chain) effects.seal(c.position.clone().add(0.0, 0.08, 0.0), 0.4)
                else ParticleApi.spawn(c.position, Particle.END_ROD, count = 2, spread = 0.08)
            }
            return
        }
        c.age++
        val from = c.position.clone()
        // Domain guidance follows moving targets. Chains always pass through terrain to their goal.
        val guided = c.attached?.takeIf { domain != null && it.isValid && !it.isDead && it.world == from.world }
        val goal = guided?.boundingBox?.center ?: if (c.chain) c.offset else null
        if (goal != null) {
            val delta = goal.clone().subtract(from.toVector())
            c.direction = if (delta.lengthSquared() < 0.00001) Vector(0.0, -0.001, 0.0)
                else delta.normalize().multiply(if (c.chain) 2.4 else 2.7)
        }
        val length = c.direction.length()
        val ray = c.direction.clone().normalize()
        val wall = if (!c.chain && guided == null) from.world.rayTraceBlocks(from, ray, length, FluidCollisionMode.NEVER, true) else null
        var distance = wall?.hitPosition?.distance(from.toVector()) ?: length
        // The last chain segment ends at the selected point, including its damage sweep.
        if (c.chain && goal != null) distance = minOf(distance, from.toVector().distance(goal))
        val hits = enemies(from.world).mapNotNull { enemy ->
            val point = CreationGeometry.contact(enemy.entity.boundingBox, from.toVector(), ray, distance, if (c.chain) 0.5 else 0.16)
            point?.let { Triple(enemy, it, it.distance(from.toVector())) }
        }.sortedBy { it.third }
        for ((enemy, point, d) in hits) {
            if (!c.hit.add(enemy.entity.uniqueId)) continue
            enemy.damage(c.impactDamage, DamageType.Normal, owner, damagePath = DamagePath.SKILL)
            ParticleApi.spawn(point.toLocation(from.world), Particle.CRIT, count = 12, spread = 0.3)
            if (!c.chain) { distance = d; c.attached = enemy.entity; break }
        }
        c.position = from.clone().add(ray.multiply(distance))
        if (c.chain && goal != null && from.toVector().distance(goal) <= length) {
            c.position = goal.toLocation(from.world); c.flying = false; c.attached = null
        }
        if (wall != null || (!c.chain && hits.isNotEmpty()) || c.age >= 30) c.flying = false
        ParticleApi.line(from, c.position, if (c.chain) Particle.CRIT else Particle.END_ROD, spacing = 0.4)
        c.display.teleport(c.position.clone().apply { yaw = 0f; pitch = 0f })
        if (c.chain) updateLinks(c)
        c.spearModel?.move(c.position, c.direction)
        if (c.display is ItemDisplay) DisplayOrientationUtil.alignSwordBladeVertically(c.display, c.direction, 0.9f)
        if (!c.flying) {
            if (c.chain) c.attached = null
            c.attached?.let { c.offset = c.position.toVector().subtract(it.location.toVector()) }
            effects.impact(c.position, c.chain)
        }
    }

    fun destroyAll(): Boolean {
        if (creations.isEmpty()) { player.sendMessage("§c파괴할 창조물이 없다."); return false }
        if (!spend(50)) return false
        creations.toList().forEach(::destroy)
        return true
    }

    private fun destroy(c: Creation) {
        val point = c.position.toVector()
        val radius = if (c.chain) 2.0 else 3.0
        enemies(c.position.world).filter { enemy ->
            CreationGeometry.inRadius(enemy.entity.boundingBox, point, radius)
        }.forEach { target ->
            if (c.chain) {
                if (combo.hit(target.entity.uniqueId, game.combatTick)) target.getOrCreateStatus(owner) { Snare() }.applyStatus(duration = 2, powerSet = 1)
                else target.getOrCreateStatus(owner) { MoveSpeedDecrease() }.applyStatus(duration = 4, powerSet = 20)
            } else target.damage(2.0, DamageType.Normal, owner, damagePath = DamagePath.SKILL)
        }
        effects.shatter(c.position, c.chain)
        if (c.chain) c.links.filterIndexed { index, _ -> index % 4 == 0 }.forEach { ParticleApi.spawn(it.location, Particle.CRIT, count = 3, spread = 0.15) }
        remove(c)
    }

    private fun updateLinks(c: Creation) {
        val displacement = c.anchor!!.clone().subtract(c.position.toVector())
        val length = displacement.length()
        val axis = if (length > 0.0001) displacement.multiply(1.0 / length) else Vector(0.0, 1.0, 0.0)
        val count = kotlin.math.ceil(length).toInt().coerceIn(0, 32)
        val rotation = Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(axis.x.toFloat(), axis.y.toFloat(), axis.z.toFloat()))
        val transform = Transformation(Vector3f(-0.5f, 0f, -0.5f).rotate(rotation), rotation, Vector3f(1f), Quaternionf())
        c.display.transformation = transform
        while (c.links.size < count) {
            val link = c.position.world.spawn(c.position, BlockDisplay::class.java).apply {
                block = Material.IRON_CHAIN.createBlockData()
                transformation = Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(1f), Quaternionf())
                setGravity(false); teleportDuration = 1
            }
            TemporaryDisplayManager.mark(link, owner.uniqueId)
            c.links += link
        }
        while (c.links.size > count) c.links.removeLast().remove()
        c.links.forEachIndexed { index, link ->
            link.transformation = transform
            link.teleport(c.position.clone().add(axis.clone().multiply(index + 1.0)).apply { yaw = 0f; pitch = 0f })
        }
    }

    private fun remove(c: Creation) { creations.remove(c); c.display.remove(); c.spearModel?.close(); c.links.forEach { it.remove() }; c.links.clear(); owner.updateStatusActionBar() }
    private fun clear() { creations.toList().forEach(::remove) }

    fun expand(): Boolean = DomainManager.expand(scope, DomainDefinition(
        name = "창조 공간", radius = 25, durationTicks = 300, floor = Material.POLISHED_BLACKSTONE,
        interiorLightLevel = 10,
        interior = CreationArchitecture::build,
        presentation = ::CreationPresentation,
        onStart = {
            domain = it
            infiniteManaDisplay = owner.getOrCreateStatus(owner) { Mana() }.displayInfinite(scope.instanceId)
            creations.forEach { c -> c.destroyAt = game.combatTick + 20 }; recoverMana()
        },
        onEnd = { domain = null; infiniteManaDisplay?.close(); infiniteManaDisplay = null; clear(); exhaustedUntil = game.combatTick + 400; owner.updateStatusActionBar() },
    ))
}

private class CreationStatus(private val text: () -> String) : org.beobma.classWarPlugin.status.StatusAbnormality() {
    override val name = "<gold>권능"
    override val description = listOf("창조물과 권능의 현재 상태를 표시한다.")
    override val canRemove = false
    override val isClassMechanic = true
    override fun actionBarText() = text()
}
