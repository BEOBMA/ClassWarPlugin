package org.beobma.classWarPlugin.gameClass.snakesword

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.*

class SnakeSwordRuntime(private val scope: AbilityScope) : EffectApiAccess {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private val game get() = scope.game
    private var busy = false
    private var readyTick = 0L
    private var pendingTarget: UUID? = null
    private var confirmed = false

    fun start() {
        // Multiply the final value: halving base 4.0 before the iron sword's -2.4 modifier would reduce speed to zero.
        val modifier = org.bukkit.attribute.AttributeModifier(
            NamespacedKey(ClassWarPlugin.instance, "snake-sword-speed-${scope.instanceId}"), -0.5,
            org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        var applied: org.bukkit.attribute.AttributeInstance? = null
        var boundPlayer: org.bukkit.entity.Player? = null
        fun clear() { applied?.removeModifier(modifier); applied = null; boundPlayer = null }
        scope.resources.own { clear() }
        object : AbilityRunnable(scope, policy = TickPolicy.SESSION) {
            override fun run() {
                val attribute = player.getAttribute(Attribute.ATTACK_SPEED)
                val enabled = scope.isActive && !scope.suspended && player.isOnline && !owner.entityStatus.isDead && holdsWeapon()
                if (!enabled || boundPlayer !== player) clear()
                if (enabled && applied == null && attribute != null) {
                    attribute.addTransientModifier(modifier); applied = attribute; boundPlayer = player
                }
            }
            override fun onCancel() { clear() }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    private fun holdsWeapon() = getWeaponClassId(player.inventory.itemInMainHand) == scope.classId

    fun confirm(context: DamageContext) {
        if (context.attacker === owner && context.target.entity.uniqueId == pendingTarget) confirmed = true
    }

    private fun hit(target: EntityData, amount: Double, basic: Boolean = false): Boolean {
        pendingTarget = target.entity.uniqueId; confirmed = false
        try {
            target.damage(amount, DamageType.Normal, owner,
                damagePath = if (basic) DamagePath.BASIC_ATTACK else DamagePath.SKILL)
            if (confirmed) {
                particles.spawn(target.entity.boundingBox.center.toLocation(target.entity.world), Particle.CRIT, count = 5, spread = 0.2, speed = 0.1)
            }
            return confirmed
        } finally { pendingTarget = null }
    }

    private fun session(ticks: Int, disarm: Boolean = false): ResourceScope {
        val resources = ResourceScope()
        val handle = scope.resources.own(isAlive = { !resources.isClosed }) { resources.close() }
        busy = true
        resources.own { busy = false; handle.forget() }
        if (disarm) {
            val status = owner.addStatus(Disarm(), owner).apply { updatePower(1); updateDuration((ticks + 19) / 20) }
            resources.own { status.cleanupFromManager() }
        }
        return resources
    }

    private fun display(at: Location, resources: ResourceScope): ItemDisplay {
        val entity = at.world.spawn(at.clone().apply { yaw = 0f; pitch = 0f }, ItemDisplay::class.java).apply {
            setItemStack(ItemStack(Material.IRON_SWORD))
            isPersistent = false
            brightness = Display.Brightness(15, 15)
            shadowRadius = 0f; shadowStrength = 0f
            interpolationDuration = 1; teleportDuration = 1
        }
        TemporaryDisplayManager.mark(entity, owner.uniqueId)
        resources.own(isAlive = { entity.isValid }) { entity.remove() }
        return entity
    }

    private fun showBlade(displays: List<ItemDisplay>, points: List<Vector>, world: World, horizontal: Boolean = false) {
        displays.forEachIndexed { index, entity ->
            val delta = points[index+1].clone().subtract(points[index])
            entity.teleport(points[index].clone().add(points[index+1]).multiply(0.5).toLocation(world))
            if (delta.lengthSquared() < 1e-8) DisplayOrientationUtil.alignSwordBladeVertically(entity, Vector(0, 1, 0), 0f)
            else if (horizontal) DisplayOrientationUtil.alignSwordBladeHorizontally(entity, delta, (delta.length() * 0.95).toFloat())
            else DisplayOrientationUtil.alignSwordBladeVertically(entity, delta, (delta.length() * 0.95).toFloat())
            entity.interpolationDelay = 0
        }
    }

    private fun sound(at: Location, sound: Sound, volume: Float, pitch: Float) = sounds.play(at, sound, volume = volume, pitch = pitch)

    fun swing(): Boolean {
        if (!scope.isActive || game.isPaused || busy || !holdsWeapon() || !owner.entityStatus.canAttack || owner.hasStatus<Disarm>() ||
            game.combatTick < readyTick || !org.beobma.classWarPlugin.damage.BasicAttackReadiness.ready(player.attackCooldown)) return false
        val attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED)?.value ?: 0.8
        readyTick = game.combatTick + ceil(20.0 / attackSpeed.coerceAtLeast(0.05)).toLong()
        player.resetCooldown()
        val world = player.world
        val range = 7.0 * ClassBalanceManager.rangeMultiplier(owner)
        val resources = session(16)
        val visuals = List(SnakeSwordGeometry.SEGMENTS) { display(player.eyeLocation, resources) }
        val hitTargets = hashSetOf<UUID>()
        fun grip() = player.eyeLocation.toVector().add(Vector(0.0, -0.35, 0.0))
        fun swingDirection(t: Int): Vector {
            val progress = (t / 12.0).coerceIn(0.0, 1.0)
            val eased = progress*progress*(3.0-2.0*progress)
            return player.eyeLocation.direction.rotateAroundY(Math.toRadians(-70.0 + 140.0*eased))
        }
        val chain = WhipChain(grip(), swingDirection(0), range)
        showBlade(visuals, chain.snapshot(), world)
        sound(player.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.65f)
        object : AbilityRunnable(scope, cancelOnDisconnect = true) {
            var tick = 0
            override fun onCancel() { resources.close() }
            override fun run() {
                if (resources.isClosed || player.world != world || ++tick > 16) { cancel(); return }
                val motion = chain.advance(grip(), swingDirection(tick), range)
                val next = motion.points
                showBlade(visuals, next, world)
                Targeting.select(owner, TargetType.Enemy, world).forEach { target ->
                    if (target.entity.uniqueId !in hitTargets && motion.contact(target.entity.boundingBox) != null && hit(target, 5.0, true))
                        hitTargets += target.entity.uniqueId
                }
                if (tick % 3 == 0) particles.spawn(next.last().toLocation(world), Particle.SWEEP_ATTACK, count = 1, spread = 0.0, speed = 0.0)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
        return true
    }

    fun extend(): Boolean {
        if (busy) return false
        val ticks = (ClassBalanceManager.scaleStatusDuration(owner, 5) * 20).coerceAtLeast(1)
        val resources = session(ticks, disarm = true)
        val world = player.world
        val range = 12.0 * ClassBalanceManager.rangeMultiplier(owner)
        val visuals = List(SnakeSwordGeometry.SEGMENTS) { display(player.eyeLocation, resources) }
        val chain = WhipChain(player.eyeLocation.toVector(), player.eyeLocation.direction, min(range, 0.5))
        var previous = chain.snapshot()
        showBlade(visuals, previous, world, horizontal = true)
        val counts = mutableMapOf<UUID, Int>()
        val lastHit = mutableMapOf<UUID, Int>()
        sound(player.location, Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.6f)
        object : AbilityRunnable(scope, cancelOnDisconnect = true) {
            var tick = 0
            override fun onCancel() { resources.close(); if (player.isOnline) sound(player.location, Sound.BLOCK_CHAIN_BREAK, 0.55f, 1.3f) }
            override fun run() {
                if (resources.isClosed || player.world != world || tick++ >= ticks) { cancel(); return }
                val motion = chain.advance(player.eyeLocation.toVector(), player.eyeLocation.direction,
                    min(range, 0.5 + tick * 1.15))
                val next = motion.points
                showBlade(visuals, next, world, horizontal = true)
                Targeting.select(owner, TargetType.Enemy, world).forEach { target ->
                    if (tick - (lastHit[target.entity.uniqueId] ?: -100) < 10) return@forEach
                    val speed = motion.contact(target.entity.boundingBox) ?: return@forEach
                    val base = SnakeSwordGeometry.speedDamage(speed)
                    if (base <= 0) return@forEach
                    val count = counts[target.entity.uniqueId] ?: 0
                    if (hit(target, SnakeSwordGeometry.repeatDamage(base, count))) {
                        counts[target.entity.uniqueId] = count + 1; lastHit[target.entity.uniqueId] = tick
                    }
                }
                if (tick % 5 == 0 && next.last().distanceSquared(previous.last()) > 0.16) {
                    sound(next.last().toLocation(world), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.4f)
                    particles.spawn(next.last().toLocation(world), Particle.CRIT, count = 3, spread = 0.1, speed = 0.02)
                }
                previous = next
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
        return true
    }

}
