package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.cos

/**
 * 플레이어 엔티티가 아닌 전투 오브젝트에 기본 공격과 투사체 충돌 판정을 제공한다.
 *
 * 표시 엔티티(Display)는 바닐라 공격/투사체 충돌 대상이 아니므로 실제 시각 요소와
 * 분리된 동적 히트박스를 이곳에 등록한다.
 */
object AttackableObjectManager {
    class Registration internal constructor(private val id: UUID) {
        fun unregister() {
            if (targets.remove(id) != null) stopProjectileTaskIfIdle()
        }
    }

    private data class Target(
        val ownerId: UUID,
        val worldId: UUID,
        val acceptsAreaSkills: Boolean,
        val canBeHitBy: (UUID?) -> Boolean,
        val hitboxes: () -> List<BoundingBox>,
        val onHit: () -> Unit,
    )

    private data class TargetHit(val target: Target, val distance: Double)

    private val targets = linkedMapOf<UUID, Target>()
    private val previousProjectileLocations = mutableMapOf<UUID, Location>()
    private var projectileTask: BukkitTask? = null

    fun start() {
        ensureProjectileTask()
    }

    private fun ensureProjectileTask() {
        if (projectileTask != null || targets.isEmpty()) return
        projectileTask = object : BukkitRunnable() {
            override fun run() = trackPhysicalProjectiles()
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
    }

    private fun stopProjectileTaskIfIdle() {
        if (targets.isNotEmpty()) return
        projectileTask?.cancel()
        projectileTask = null
        previousProjectileLocations.clear()
    }

    fun shutdown() {
        projectileTask?.cancel()
        projectileTask = null
        previousProjectileLocations.clear()
        targets.clear()
    }

    fun register(
        ownerId: UUID,
        world: World,
        acceptsAreaSkills: Boolean = false,
        canBeHitBy: (UUID?) -> Boolean = { attackerId -> attackerId != ownerId },
        hitboxes: () -> List<BoundingBox>,
        onHit: () -> Unit,
    ): Registration {
        val id = UUID.randomUUID()
        targets[id] = Target(ownerId, world.uid, acceptsAreaSkills, canBeHitBy, hitboxes, onHit)
        ensureProjectileTask()
        return Registration(id)
    }

    /** 커스텀 스킬 투사체의 한 틱 이동 구간을 검사한다. */
    fun hitProjectileSegment(
        attackerId: UUID,
        start: Location,
        end: Location,
        expansion: Double = 0.0,
    ): Boolean = hitUnobstructedProjectile(attackerId, start, end, expansion)

    /** 범위형 스킬에 포함된 모든 전투 오브젝트를 적중시킨다. */
    fun hitSphere(attackerId: UUID, center: Location, radius: Double): Int {
        if (radius < 0.0) return 0
        val centerVector = center.toVector()
        val hitTargets = targets.values.toList().filter { target ->
            target.worldId == center.world.uid &&
                target.acceptsAreaSkills &&
                target.ownerId != attackerId &&
                target.canBeHitBy(attackerId) &&
                target.hitboxes().any { HitboxUtil.intersectsSphere(it, centerVector, radius) }
        }
        hitTargets.forEach { it.onHit() }
        return hitTargets.size
    }

    /** 원뿔형 스킬에 포함된 모든 전투 오브젝트를 적중시킨다. */
    fun hitCone(attackerId: UUID, origin: Location, radius: Double, angle: Double): Int {
        if (radius < 0.0) return 0
        val direction = origin.direction.clone()
        if (direction.lengthSquared() < 1.0E-9) return 0
        direction.normalize()
        val originVector = origin.toVector()
        val minimumDot = cos(Math.toRadians(angle / 2.0))
        val hitTargets = targets.values.toList().filter { target ->
            if (!target.acceptsAreaSkills || target.worldId != origin.world.uid ||
                target.ownerId == attackerId || !target.canBeHitBy(attackerId)
            ) {
                return@filter false
            }
            target.hitboxes().any { box ->
                val closest = HitboxUtil.closestPoint(box, originVector)
                val toTarget = closest.subtract(originVector.clone())
                val distanceSquared = toTarget.lengthSquared()
                distanceSquared <= radius * radius &&
                    (distanceSquared < 1.0E-9 || direction.dot(toTarget.normalize()) >= minimumDot)
            }
        }
        hitTargets.forEach { it.onHit() }
        return hitTargets.size
    }

    /**
     * 기본 공격 레이에서 가장 먼저 맞는 오브젝트를 처리한다.
     * 블록이나 다른 생명체가 먼저 있으면 오브젝트가 공격을 가로채지 않는다.
     */
    fun hitBasicAttack(player: Player, maximumDistance: Double = 3.25): Boolean {
        val start = player.eyeLocation
        val direction = start.direction.clone()
        if (direction.lengthSquared() < 1.0E-9) return false
        direction.normalize()
        val end = start.clone().add(direction.clone().multiply(maximumDistance))
        val objectHit = findFirst(player.uniqueId, start, end, expansion = 0.08) ?: return false

        val blockDistance = player.world.rayTraceBlocks(start, direction, maximumDistance)
            ?.hitPosition?.distance(start.toVector())
        if (blockDistance != null && blockDistance <= objectHit.distance + 1.0E-6) return false

        val livingDistance = nearestLivingEntityDistance(
            player.world,
            player.uniqueId,
            start.toVector(),
            direction,
            maximumDistance,
        )
        if (livingDistance != null && livingDistance <= objectHit.distance + 1.0E-6) return false

        objectHit.target.onHit()
        return true
    }

    private fun trackPhysicalProjectiles() {
        if (targets.isEmpty()) {
            previousProjectileLocations.clear()
            return
        }
        val targetWorldIds = targets.values.mapTo(mutableSetOf()) { it.worldId }
        val seen = mutableSetOf<UUID>()
        ClassWarPlugin.instance.server.worlds
            .asSequence()
            .filter { it.uid in targetWorldIds }
            .flatMap { it.getEntitiesByClass(Projectile::class.java).asSequence() }
            .filter { it.isValid }
            .forEach { projectile ->
                val projectileId = projectile.uniqueId
                seen += projectileId
                val current = projectile.location
                val previous = previousProjectileLocations[projectileId]
                    ?.takeIf { it.world == current.world }
                    ?: current.clone().subtract(projectile.velocity)
                val shooterId = (projectile.shooter as? Entity)?.uniqueId
                if (hitUnobstructedProjectile(shooterId, previous, current, expansion = 0.15)) {
                    projectile.remove()
                    previousProjectileLocations.remove(projectileId)
                } else {
                    previousProjectileLocations[projectileId] = current.clone()
                }
            }
        previousProjectileLocations.keys.retainAll(seen)
    }

    private fun hitUnobstructedProjectile(
        attackerId: UUID?,
        start: Location,
        end: Location,
        expansion: Double,
    ): Boolean {
        val hit = findFirst(attackerId, start, end, expansion) ?: return false
        val movement = end.toVector().subtract(start.toVector())
        val distance = movement.length()
        if (distance < 1.0E-9) return false
        val direction = movement.normalize()

        val blockDistance = start.world.rayTraceBlocks(start, direction, distance)
            ?.hitPosition?.distance(start.toVector())
        if (blockDistance != null && blockDistance <= hit.distance + 1.0E-6) return false

        val livingDistance = nearestLivingEntityDistance(
            start.world,
            attackerId,
            start.toVector(),
            direction,
            distance,
        )
        if (livingDistance != null && livingDistance <= hit.distance + 1.0E-6) return false

        hit.target.onHit()
        return true
    }

    private fun findFirst(attackerId: UUID?, start: Location, end: Location, expansion: Double): TargetHit? {
        if (start.world != end.world) return null
        val movement = end.toVector().subtract(start.toVector())
        val distance = movement.length()
        if (distance < 1.0E-9) return null
        return targets.values.toList().asSequence()
            .filter { target ->
                target.worldId == start.world.uid &&
                    target.ownerId != attackerId &&
                    target.canBeHitBy(attackerId)
            }
            .mapNotNull { target ->
                target.hitboxes().asSequence()
                    .mapNotNull { box ->
                        HitboxUtil.rayIntersectionDistance(box, start.toVector(), movement, distance, expansion)
                    }
                    .minOrNull()
                    ?.let { TargetHit(target, it) }
            }
            .minByOrNull(TargetHit::distance)
    }

    private fun nearestLivingEntityDistance(
        world: World,
        attackerId: UUID?,
        origin: Vector,
        direction: Vector,
        maximumDistance: Double,
    ): Double? = world.livingEntities.asSequence()
        .filter { it.uniqueId != attackerId && it.isValid }
        .mapNotNull { entity ->
            HitboxUtil.rayIntersectionDistance(
                entity.boundingBox,
                origin,
                direction,
                maximumDistance,
                expansion = 0.08,
            )
        }
        .minOrNull()
}
