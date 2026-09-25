package org.beobma.classWarPlugin.gameClass.afterglow

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.*
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.creator.CreationGeometry
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import io.papermc.paper.datacomponent.item.ResolvableProfile
import org.beobma.classWarPlugin.status.list.Aftermath
import org.beobma.classWarPlugin.status.list.Resonance
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.*

class AfterglowRuntime(private val scope: AbilityScope) {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private var clock = 0L
    private data class Echo(var at: Location, val mannequin: Mannequin, val baseDamage: Double,
        var expires: Long, val chargeTargets: ArrayDeque<EntityData> = ArrayDeque())
    private val echoes = mutableListOf<Echo>()
    private var pending: Pair<UUID, () -> Unit>? = null

    fun start() {
        scope.resources.own { echoes.toList().forEach(::remove) }
        object : AbilityRunnable(scope) {
            override fun run() { clock++; echoes.toList().forEach(::tick) }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    fun confirmed(context: DamageContext) {
        val callback = pending
        if (callback != null && callback.first == context.target.entity.uniqueId) {
            pending = null; callback.second(); return
        }
        if (!context.path.isBasicAttack || context.secondaryAttack) return
        context.target.getOrCreateStatus(owner) { Aftermath() }.increasePower(5)
        create(context.baseDamage)
    }

    private fun hit(target: EntityData, amount: Double, basic: Boolean = false, after: () -> Unit = {}): Boolean {
        var confirmed = false
        val previous = pending
        pending = target.entity.uniqueId to { confirmed = true; after() }
        try {
            target.damage(amount, DamageType.Normal, owner,
                damagePath = if (basic) DamagePath.BASIC_ATTACK else DamagePath.SKILL, secondaryAttack = true)
        } finally { pending = previous }
        return confirmed
    }

    private fun enemies(at: Location, radius: Double) = Targeting.select(owner, TargetType.Enemy, at.world)
        .filter { CreationGeometry.inRadius(it.entity.boundingBox, at.toVector(), radius) }

    private fun create(baseDamage: Double) {
        if (echoes.size >= 5) remove(echoes.first())
        val at = player.location.clone()
        val mannequin = at.world.spawn(at, Mannequin::class.java) { entity ->
            entity.addScoreboardTag("cw-afterglow-echo")
            entity.isPersistent = false
            entity.isInvulnerable = true
            entity.isSilent = true
            entity.isCollidable = false
            entity.isImmovable = true
            entity.setGravity(false)
            entity.setAI(false)
            entity.setCanPickupItems(false)
            entity.profile = ResolvableProfile.resolvableProfile(player.playerProfile)
            entity.description = null
            entity.equipment.armorContents = player.inventory.armorContents.map { it?.clone() }.toTypedArray()
            entity.equipment.setItemInMainHand(player.inventory.itemInMainHand.clone())
            entity.equipment.setItemInOffHand(player.inventory.itemInOffHand.clone())
            // Mannequin is not a Mob: setDropChance throws on Paper.
            // Normal cleanup removes it directly; OnEntityDeathEvent guards unexpected deaths.
        }
        val echo = Echo(at, mannequin, baseDamage, clock + 100)
        echoes += echo
        move(echo, at)
        ParticleApi.spawn(at.clone().add(0.0, 1.0, 0.0), Particle.REVERSE_PORTAL, 20, 0.4, 0.03)
        SoundApi.play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f)
    }

    private fun move(echo: Echo, at: Location) {
        echo.at = at.clone()
        echo.mannequin.teleport(at)
    }

    private fun tick(echo: Echo) {
        if (echo.at.world != player.world || !echo.mannequin.isValid) { remove(echo); return }
        while (echo.chargeTargets.isNotEmpty()) {
            val target = echo.chargeTargets.first()
            if (!target.entity.isValid || target.entity.isDead || target.entity.world != echo.at.world || !Targeting.isEnemy(owner, target)) {
                echo.chargeTargets.removeFirst(); continue
            }
            val from = echo.at.clone().add(0.0, 0.9, 0.0)
            val delta = target.entity.boundingBox.center.subtract(from.toVector())
            val length = minOf(1.25, delta.length())
            val ray = if (delta.lengthSquared() > 1e-8) delta.normalize() else Vector(0.0, 1.0, 0.0)
            val point = CreationGeometry.contact(target.entity.boundingBox, from.toVector(), ray, length, 0.25)
            val next = point?.toLocation(from.world) ?: from.clone().add(ray.multiply(length))
            ParticleApi.line(from, next, Particle.WITCH, spacing = 0.25)
            move(echo, next.clone().subtract(0.0, 0.9, 0.0))
            if (point != null) {
                hit(target, 1.0)
                ParticleApi.spawn(next, Particle.SWEEP_ATTACK, 1)
                echo.chargeTargets.removeFirst()
            }
            return
        }
        if (clock >= echo.expires) { strike(echo, false); remove(echo); return }
        if (clock % 10 == 0L) ParticleApi.spawn(echo.at.clone().add(0.0, 1.0, 0.0), Particle.WITCH, 3, 0.35)
    }

    private fun strike(echo: Echo, transfer: Boolean): Boolean {
        val target = enemies(echo.at.clone().add(0.0, 0.9, 0.0), 3.0)
            .minByOrNull { it.entity.boundingBox.center.distanceSquared(echo.at.toVector()) } ?: return false
        var keep = false
        val center = target.entity.boundingBox.center.toLocation(echo.at.world)
        ParticleApi.line(echo.at.clone().add(0.0, 1.0, 0.0), center, Particle.WITCH, spacing = 0.2)
        ParticleApi.spawn(center, Particle.SWEEP_ATTACK, 1)
        SoundApi.play(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 1.4f)
        echo.mannequin.swingMainHand()
        hit(target, echo.baseDamage * 0.5, basic = true) {
            if (transfer) {
                keep = target.getStatus<Resonance>()?.consume() == true
                target.getOrCreateStatus(owner) { Aftermath() }.increasePower(10)
            }
        }
        if (keep) echo.expires = clock + 100
        return keep
    }

    fun spin(): Boolean {
        val charged = mutableSetOf<UUID>()
        val snapshot = echoes.toList()
        snapshot.forEach { echo ->
            object : AbilityRunnable(scope) {
                var frames = 0
                override fun run() {
                    if (echo !in echoes) { cancel(); return }
                    move(echo, echo.at.clone().apply { yaw += 45f })
                    if (++frames >= 8) cancel()
                }
            }.runTaskTimer(ClassWarPlugin.instance,1,1)
        }
        val centers = listOf(player.location.clone() to 5.0) + snapshot.map { it.at.clone() to 3.0 }
        for ((at, damage) in centers) {
            swirl(at)
            for (target in enemies(at.clone().add(0.0, 0.9, 0.0), 5.0)) hit(target, damage) {
                if (target.entity.uniqueId !in charged && target.getStatus<Resonance>()?.consume() == true) {
                    charged += target.entity.uniqueId
                    snapshot.filter { it in echoes }.forEach { it.chargeTargets.addLast(target) }
                }
                target.getOrCreateStatus(owner) { Aftermath() }.increasePower(20)
            }
        }
        return true
    }

    private fun swirl(at: Location) {
        SoundApi.play(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.65f, 0.7f)
        object : AbilityRunnable(scope) {
            var frame = 0
            override fun run() {
                repeat(18) { i ->
                    val angle = frame * 0.5 + i * 2 * PI / 18
                    ParticleApi.spawn(at.clone().add(cos(angle)*5, 0.3 + frame*0.08, sin(angle)*5), Particle.WITCH)
                }
                if (++frame >= 8) cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    fun swap(): Boolean {
        val eye = player.eyeLocation
        val ray = eye.direction
        val wall = eye.world.rayTraceBlocks(eye, ray, 12.0)?.hitPosition?.distance(eye.toVector()) ?: 12.0
        val selected = echoes.filter { it.at.world == eye.world }.mapNotNull { echo ->
            val box = echo.mannequin.boundingBox.clone().expand(0.15)
            box.rayTrace(eye.toVector(), ray, wall)?.let { echo to it.hitPosition.distance(eye.toVector()) }
        }.minByOrNull { it.second }?.first
        if (selected == null) { player.sendMessage("§c12칸 내의 분신을 바라보아야 한다."); return false }
        val from = player.location.clone()
        val to = selected.at.clone().apply { yaw = from.yaw; pitch = from.pitch }
        val box = player.boundingBox.clone().shift(to.toVector().subtract(from.toVector()))
        val border = to.world.worldBorder
        val half = border.size / 2
        if (box.minX < border.center.x-half || box.maxX > border.center.x+half ||
            box.minZ < border.center.z-half || box.maxZ > border.center.z+half) return false
        for (x in floor(box.minX).toInt()..floor(box.maxX-1e-6).toInt())
            for (y in floor(box.minY).toInt()..floor(box.maxY-1e-6).toInt())
                for (z in floor(box.minZ).toInt()..floor(box.maxZ-1e-6).toInt())
                    if (!to.world.getBlockAt(x,y,z).isPassable) { player.sendMessage("§c분신의 위치가 막혀 있다."); return false }
        if (!player.teleport(to)) return false
        player.fallDistance = 0f
        move(selected, from)
        ParticleApi.line(from.clone().add(0.0,1.0,0.0), to.clone().add(0.0,1.0,0.0), Particle.REVERSE_PORTAL, spacing = 0.25)
        SoundApi.play(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.3f)
        if (!strike(selected, true)) remove(selected)
        return true
    }

    private fun remove(echo: Echo) {
        echoes.remove(echo)
        echo.mannequin.remove()
    }
}
