package org.beobma.classWarPlugin.gameClass.bomb

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.list.Charge
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

internal object BombRules {
    const val LIMIT = 5
    const val COST = 10
    const val FUSE_TICKS = 5
    fun damage(bombs: Int, alreadyHit: Boolean) = if (alreadyHit) 2.0 else bombs.coerceIn(1, LIMIT).toDouble()
}

/** Display-only bombs: never spawn primed TNT or call World.createExplosion. */
class BombRuntime(private val scope: AbilityScope) : EffectApiAccess {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private data class Bomb(val display: BlockDisplay, var at: Location, var velocity: Vector,
                            var grounded: Boolean = false, var armed: Boolean = false)
    private data class Salvo(val bombs: List<Bomb>, var fuse: Int = BombRules.FUSE_TICKS)
    private val bombs = mutableListOf<Bomb>()
    private val salvos = mutableListOf<Salvo>()
    private lateinit var charge: Charge
    private var tick = 0

    fun start() {
        charge = owner.getOrCreateStatus(owner) { Charge() }
        scope.resources.own { bombs.forEach { it.display.remove() }; bombs.clear(); salvos.clear() }
        object : AbilityRunnable(scope) {
            override fun onCancel() { bombs.forEach { it.display.remove() }; bombs.clear(); salvos.clear() }
            override fun run() {
                tick++
                if (player.isSneaking) charge.addCharge(1)
                bombs.toList().forEach { bomb ->
                    if (!bomb.display.isValid || bomb.at.y < bomb.at.world.minHeight - 4 || bomb.at.y >= bomb.at.world.maxHeight) {
                        bomb.display.remove(); bombs.remove(bomb); return@forEach
                    }
                    if (bomb.grounded && bomb.at.world.rayTraceBlocks(bomb.at, Vector(0,-1,0), 0.3, FluidCollisionMode.NEVER, true) == null)
                        bomb.grounded = false
                    if (!bomb.grounded) move(bomb)
                    if (tick % 4 == 0) particles.spawn(bomb.at.clone().add(0.0, 0.24, 0.0), Particle.SMOKE, count = 1, spread = 0.015, speed = 0.005)
                }
                salvos.toList().forEach { salvo ->
                    salvo.bombs.filter { it.display.isValid }.forEach {
                        it.display.block = (if (salvo.fuse % 2 == 0) Material.WHITE_CONCRETE else Material.TNT).createBlockData()
                    }
                    if (--salvo.fuse <= 0) { salvos.remove(salvo); explode(salvo) }
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    fun clearForSuspend() { bombs.forEach { it.display.remove() }; bombs.clear(); salvos.clear() }

    private fun reject(message: String): Boolean { player.sendMiniMessage("<red><bold>[!] $message"); return false }
    fun canThrow(): Boolean = when {
        bombs.size >= BombRules.LIMIT -> reject("폭탄은 최대 5개까지 설치할 수 있습니다.")
        !charge.hasCharge(BombRules.COST) -> reject("충전이 10 이상 필요합니다.")
        else -> true
    }
    fun canDetonate(): Boolean {
        val count = bombs.count { !it.armed && it.display.isValid }
        if (count == 0) return reject("기폭할 폭탄이 없습니다.")
        return charge.hasCharge(count * BombRules.COST) || reject("충전이 ${count * BombRules.COST} 이상 필요합니다.")
    }

    fun throwBomb(): Boolean {
        if (!canThrow()) return false
        val at = player.eyeLocation.clone()
        val display = at.world.spawn(at, BlockDisplay::class.java).apply {
            block = Material.TNT.createBlockData(); isPersistent = false
            brightness = Display.Brightness(15, 15); shadowRadius = 0.15f
            teleportDuration = 1
            transformation = Transformation(Vector3f(-0.18f), Quaternionf(), Vector3f(0.36f), Quaternionf())
        }
        TemporaryDisplayManager.mark(display, owner.uniqueId)
        if (!charge.consumeCharge(BombRules.COST)) { display.remove(); return false }
        bombs += Bomb(display, at, at.direction.multiply(0.9).add(Vector(0.0, 0.16, 0.0)))
        sounds.play(at, Sound.ENTITY_SNOWBALL_THROW, volume = 0.7f, pitch = 0.65f)
        return true
    }

    private fun move(bomb: Bomb) {
        val delta = bomb.velocity
        if (delta.lengthSquared() > 1e-8) {
            val hit = bomb.at.world.rayTraceBlocks(bomb.at, delta, delta.length() + 0.18, FluidCollisionMode.NEVER, true)
            if (hit != null) {
                val normal = hit.hitBlockFace?.direction ?: Vector(0, 1, 0)
                bomb.at = hit.hitPosition.toLocation(bomb.at.world).add(normal.clone().multiply(0.2))
                bomb.velocity = delta.clone().subtract(normal.clone().multiply(delta.dot(normal))).multiply(0.45)
                if (normal.y > 0.5) { bomb.grounded = true; bomb.velocity.zero() }
                sounds.play(bomb.at, Sound.BLOCK_METAL_HIT, volume = 0.35f, pitch = 1.4f)
            } else bomb.at.add(delta)
        }
        if (!bomb.grounded) bomb.velocity.multiply(0.98).add(Vector(0.0, -0.045, 0.0))
        bomb.display.teleport(bomb.at)
    }

    fun detonate(): Boolean {
        if (!canDetonate()) return false
        val selected = bombs.filter { !it.armed && it.display.isValid }
        if (!charge.consumeCharge(selected.size * BombRules.COST)) return false
        selected.forEach { it.armed = true }
        salvos += Salvo(selected)
        sounds.play(player, Sound.BLOCK_LEVER_CLICK, volume = 0.8f, pitch = 0.6f)
        selected.forEach { sounds.play(it.at, Sound.ENTITY_TNT_PRIMED, volume = 0.35f, pitch = 1.8f) }
        return true
    }

    private fun explode(salvo: Salvo) {
        val hit = hashSetOf<UUID>()
        val radius = 3.0 * ClassBalanceManager.rangeMultiplier(owner)
        salvo.bombs.forEach { bomb ->
            if (!scope.isActive || !bomb.display.isValid) return@forEach
            bomb.display.remove(); bombs.remove(bomb)
            particles.spawn(bomb.at, Particle.EXPLOSION, count = 2, spread = 0.4, speed = 0.0)
            particles.spawn(bomb.at, Particle.SMOKE, count = 15, spread = 0.65, speed = 0.12)
            particles.circle(bomb.at, Particle.CRIT, radius, 32)
            sounds.play(bomb.at, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.75f, pitch = 0.85f)
            Targeting.select(owner, TargetType.Enemy, bomb.at.world).filter {
                HitboxUtil.intersectsSphere(it.entity.boundingBox, bomb.at.toVector(), radius)
            }.forEach { target ->
                target.damage(BombRules.damage(salvo.bombs.size, !hit.add(target.entity.uniqueId)),
                    DamageType.Normal, owner, damagePath = DamagePath.SKILL)
            }
        }
    }
}
