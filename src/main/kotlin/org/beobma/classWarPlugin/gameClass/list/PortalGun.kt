package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.SkillInputHandler
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile as BukkitProjectile
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class PortalGun : GameClass(), SkillInputHandler {
    override val name = "<gray>포탈건"
    override val rank = Rank.B
    override val classItemMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE
    private val portalSkill = RedSkill()
    override var skills: List<Skill> = listOf(portalSkill)
    override var passives: List<BasePassive> = listOf()

    override fun prepareSkillInput(event: PlayerInteractEvent, skill: Skill): Boolean {
        if (skill !== portalSkill) return event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK
        portalSkill.nextColor = when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> PortalColor.BLUE
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> PortalColor.ORANGE
            else -> return false
        }
        return true
    }

    private enum class PortalColor(
        val material: Material,
        val dustColor: Color,
        val label: String,
    ) {
        BLUE(Material.LIGHT_BLUE_STAINED_GLASS, Color.fromRGB(30, 140, 255), "파란색"),
        ORANGE(Material.ORANGE_STAINED_GLASS, Color.fromRGB(255, 135, 20), "노란색"),
    }

    private data class Placement(val location: Location, val face: BlockFace)

    private data class PortalData(
        val center: Location,
        val normal: Vector,
        val up: Vector,
        val right: Vector,
        val color: PortalColor,
        val display: BlockDisplay,
    )

    private data class PortalMomentum(
        var chain: Int,
        var lastTransitTick: Long,
    )

    private inner class RedSkill : Skill() {
        override val name = "<bold>포탈건 발사"
        override val description = listOf(
            "<gray>좌클릭 시 파란색 포탈을 발사한다.",
            "<gray>우클릭 시 노란색 포탈을 발사한다.", "",
            "<gray>두 포탈이 모두 설치되면 서로 연결된다.",
            "<gray>이 스킬의 재사용 대기 시간은 포탈이 연결된 후에 적용된다."
        )
        override val cooldown = 30

        var nextColor = PortalColor.ORANGE
        private var pendingPlacement: Placement? = null
        private var bluePortal: PortalData? = null
        private var orangePortal: PortalData? = null
        private var portalTask: BukkitTask? = null
        private val entityCooldowns = mutableMapOf<UUID, Long>()
        private val previousEntityCenters = mutableMapOf<UUID, Vector>()
        private val portalMomentumByEntity = mutableMapOf<UUID, PortalMomentum>()

        override fun isUseSuccess(): Boolean {
            val hit = player.world.rayTraceBlocks(
                player.eyeLocation,
                player.eyeLocation.direction,
                45.0,
                FluidCollisionMode.NEVER,
                true,
            )
            val position = hit?.hitPosition
            val face = hit?.hitBlockFace
            if (position == null || face == null) {
                player.sendMiniMessage("<red><bold>[!] 45칸 내의 포탈을 설치할 블록을 바라봐야 합니다.")
                return false
            }
            pendingPlacement = Placement(position.toLocation(player.world), face)
            return true
        }

        override fun use() {
            val placement = pendingPlacement ?: return
            pendingPlacement = null
            val portal = createPortal(placement, nextColor)
            when (nextColor) {
                PortalColor.BLUE -> {
                    bluePortal?.display?.remove()
                    bluePortal = portal
                }
                PortalColor.ORANGE -> {
                    orangePortal?.display?.remove()
                    orangePortal = portal
                }
            }
            ensurePortalTask()

            particles.spawn(
                portal.center,
                Particle.DUST,
                Particle.DustOptions(nextColor.dustColor, 1.7f),
                org.beobma.classWarPlugin.effect.ParticleOptions.spread(30, 0.65, 0.08),
            )
            sounds.play(portal.center, Sound.ENTITY_ENDER_EYE_LAUNCH, volume = 0.85f, pitch = if (nextColor == PortalColor.BLUE) 1.5f else 0.9f)
            if (bluePortal == null || orangePortal == null) {
                multiplyCurrentCooldown(0.0)
            } else {
                activePortalSkills += this
                sounds.play(player, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.75f, pitch = 1.65f)
                particles.spawn(player, Particle.PORTAL, count = 30, spread = 0.55, speed = 0.12)
            }
        }

        private fun createPortal(placement: Placement, color: PortalColor): PortalData {
            val normal = placement.face.direction.normalize()
            val up = if (abs(normal.y) > 0.9) Vector(0.0, 0.0, 1.0) else Vector(0.0, 1.0, 0.0)
            val right = up.clone().crossProduct(normal).normalize()
            val center = placement.location.clone().add(normal.clone().multiply(0.09))
            val rotation = Quaternionf().rotationTo(
                Vector3f(0f, 0f, 1f),
                Vector3f(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat()),
            )
            val scale = Vector3f(1.6f, 2.7f, 0.05f)
            val rotatedCenter = rotation.transform(Vector3f(scale.x * 0.5f, scale.y * 0.5f, scale.z * 0.5f))
            val display = center.world.spawn(center, BlockDisplay::class.java).apply {
                block = color.material.createBlockData()
                isPersistent = false
                brightness = org.bukkit.entity.Display.Brightness(15, 15)
                glowColorOverride = color.dustColor
                transformation = Transformation(
                    Vector3f(-rotatedCenter.x, -rotatedCenter.y, -rotatedCenter.z),
                    rotation,
                    scale,
                    Quaternionf(),
                )
                TemporaryDisplayManager.mark(this, player.uniqueId)
            }
            return PortalData(center, normal, up, right, color, display)
        }

        private fun ensurePortalTask() {
            if (portalTask != null) return
            allPortalSkills += this
            portalTask = playerData.trackTask(object : BukkitRunnable() {
                private var ticks = 0

                override fun run() {
                    if (!player.isOnline || playerStatus.isDead || (bluePortal == null && orangePortal == null)) {
                        clearPortals()
                        cancel()
                        return
                    }
                    bluePortal?.let { drawPortal(it, ticks) }
                    orangePortal?.let { drawPortal(it, ticks) }
                    val blue = bluePortal
                    val orange = orangePortal
                    if (blue != null && orange != null) {
                        teleportBukkitEntities(blue, orange)
                    }
                    entityCooldowns.entries.removeIf { player.world.fullTime - it.value > 20L }
                    portalMomentumByEntity.entries.removeIf { player.world.fullTime - it.value.lastTransitTick > 100L }
                    ticks++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }

        private fun drawPortal(portal: PortalData, tick: Int) {
            val dust = Particle.DustOptions(portal.color.dustColor, 1.25f)
            repeat(18) { index ->
                val angle = 2.0 * PI * index / 18.0 + tick * 0.045
                val point = portal.center.clone()
                    .add(portal.right.clone().multiply(cos(angle) * 0.88))
                    .add(portal.up.clone().multiply(sin(angle) * 1.34))
                    .add(portal.normal.clone().multiply(0.04))
                particles.spawn(point, Particle.DUST, dust, org.beobma.classWarPlugin.effect.ParticleOptions(count = 1))
            }
            if (tick % 3 == 0) particles.spawn(portal.center, Particle.PORTAL, count = 3, spread = 0.45, speed = 0.02)
        }

        private fun teleportBukkitEntities(blue: PortalData, orange: PortalData) {
            val worlds = listOf(blue.center.world, orange.center.world).distinct()
            val candidates = worlds.asSequence()
                .flatMap { it.entities.asSequence() }
                .filter { it is Player || it is BukkitProjectile }
                .distinctBy { it.uniqueId }
                .toList()
            val currentIds = mutableSetOf<UUID>()
            for (entity in candidates) {
                currentIds += entity.uniqueId
                val now = entity.world.fullTime
                val currentCenter = entity.boundingBox.center
                val previousCenter = previousEntityCenters.put(entity.uniqueId, currentCenter.clone())
                    ?: currentCenter.clone().subtract(entity.velocity)
                if (now < entityCooldowns.getOrDefault(entity.uniqueId, Long.MIN_VALUE)) continue
                val route = when {
                    entity.world == blue.center.world && crossesPortal(blue, previousCenter, currentCenter, entity) -> blue to orange
                    entity.world == orange.center.world && crossesPortal(orange, previousCenter, currentCenter, entity) -> orange to blue
                    else -> null
                } ?: continue
                if (teleportEntity(entity, route.first, route.second)) {
                    entityCooldowns[entity.uniqueId] = now + 1L
                    previousEntityCenters[entity.uniqueId] = entity.boundingBox.center
                }
            }
            previousEntityCenters.keys.removeIf { it !in currentIds }
        }

        private fun crossesPortal(
            portal: PortalData,
            previousCenter: Vector,
            currentCenter: Vector,
            entity: Entity,
        ): Boolean {
            val movement = currentCenter.clone().subtract(previousCenter)
            val inwardMovement = movement.dot(portal.normal)
            val inwardVelocity = entity.velocity.dot(portal.normal)
            if (inwardMovement >= -1.0E-5 && inwardVelocity >= -0.015) return false

            val boundingBox = entity.boundingBox
            val halfX = (boundingBox.maxX - boundingBox.minX) * 0.5
            val halfY = (boundingBox.maxY - boundingBox.minY) * 0.5
            val halfZ = (boundingBox.maxZ - boundingBox.minZ) * 0.5
            val normalExtent = abs(portal.normal.x) * halfX + abs(portal.normal.y) * halfY + abs(portal.normal.z) * halfZ
            val triggerCenter = portal.center.toVector().add(portal.normal.clone().multiply(normalExtent + 0.12))
            val startDistance = previousCenter.clone().subtract(triggerCenter).dot(portal.normal)
            val endDistance = currentCenter.clone().subtract(triggerCenter).dot(portal.normal)
            val denominator = endDistance - startDistance

            if (denominator < -1.0E-7) {
                val progress = -startDistance / denominator
                if (progress in -0.08..1.25) {
                    val hitPoint = previousCenter.clone().add(movement.clone().multiply(progress.coerceIn(0.0, 1.0)))
                    if (insidePortal(portal, hitPoint, halfX, halfY, halfZ)) return true
                }
            }

            val normalDistance = currentCenter.clone().subtract(portal.center.toVector()).dot(portal.normal)
            return normalDistance in -0.15..(normalExtent + 0.3) &&
                insidePortal(portal, currentCenter, halfX, halfY, halfZ)
        }

        private fun teleportEntity(entity: Entity, entry: PortalData, exit: PortalData): Boolean {
            val transformedVelocity = transformVector(entry, exit, entity.velocity)
            val outwardSpeed = transformedVelocity.dot(exit.normal)
            val minimumOutwardSpeed = if (entity is Player) 0.42 else 0.28
            if (outwardSpeed < minimumOutwardSpeed) {
                transformedVelocity.add(exit.normal.clone().multiply(minimumOutwardSpeed - outwardSpeed))
            }

            val boundingBox = entity.boundingBox
            val halfX = (boundingBox.maxX - boundingBox.minX) * 0.5
            val halfY = (boundingBox.maxY - boundingBox.minY) * 0.5
            val halfZ = (boundingBox.maxZ - boundingBox.minZ) * 0.5
            val exitExtent = abs(exit.normal.x) * halfX + abs(exit.normal.y) * halfY + abs(exit.normal.z) * halfZ
            val entityCenterOffset = entity.location.toVector().subtract(boundingBox.center)
            val destinationCenter = exit.center.toVector()
                .add(exit.normal.clone().multiply(exitExtent + 0.22))
            val destination = destinationCenter.add(entityCenterOffset).toLocation(exit.center.world)
            if (entity is Player) {
                val facing = transformVector(entry, exit, entity.location.direction).normalize()
                destination.direction = facing
                if (!entity.teleport(destination)) return false
                entity.fallDistance = 0f
            } else {
                if (!entity.teleport(destination)) return false
            }
            val momentumChain = applyVerticalLoopMomentum(entity, entry, exit, transformedVelocity)
            entity.velocity = transformedVelocity
            particles.spawn(entry.center, Particle.PORTAL, count = 18 + momentumChain, spread = 0.5, speed = 0.1 + momentumChain * 0.004)
            particles.spawn(exit.center, Particle.REVERSE_PORTAL, count = 22 + momentumChain * 2, spread = 0.55, speed = 0.12 + momentumChain * 0.006)
            sounds.play(entry.center, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.6f, pitch = 1.35f)
            sounds.play(
                exit.center,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                volume = (0.75f + momentumChain * 0.025f).coerceAtMost(1.2f),
                pitch = (1.55f + momentumChain * 0.025f).coerceAtMost(2.0f),
            )
            if (momentumChain >= 4) {
                particles.spawn(entity, Particle.CLOUD, count = momentumChain.coerceAtMost(14), spread = 0.24, speed = 0.08)
                sounds.play(entity, Sound.ENTITY_WIND_CHARGE_WIND_BURST, volume = 0.35f, pitch = (1.1f + momentumChain * 0.025f).coerceAtMost(1.65f))
            }
            return true
        }

        private fun applyVerticalLoopMomentum(
            entity: Entity,
            entry: PortalData,
            exit: PortalData,
            velocity: Vector,
        ): Int {
            val isVerticalLoop = abs(entry.normal.y) >= 0.9 &&
                abs(exit.normal.y) >= 0.9 &&
                entry.normal.dot(exit.normal) <= -0.9
            if (!isVerticalLoop) {
                portalMomentumByEntity.remove(entity.uniqueId)
                return 0
            }

            val now = exit.center.world.fullTime
            val momentum = portalMomentumByEntity[entity.uniqueId]
            val chain = if (momentum != null && now - momentum.lastTransitTick <= 100L) {
                (momentum.chain + 1).coerceAtMost(20)
            } else {
                1
            }
            portalMomentumByEntity[entity.uniqueId] = PortalMomentum(chain, now)

            val currentSpeed = velocity.length()
            if (currentSpeed > 1.0E-6) {
                val speedGain = (0.075 + chain * 0.022).coerceAtMost(0.42)
                val maximumSpeed = if (entity is Player) 4.2 else 6.0
                velocity.multiply((currentSpeed + speedGain).coerceAtMost(maximumSpeed) / currentSpeed)
            }
            return chain
        }

        private fun insidePortal(
            portal: PortalData,
            point: Vector,
            halfX: Double = 0.0,
            halfY: Double = 0.0,
            halfZ: Double = 0.0,
        ): Boolean {
            val relative = point.clone().subtract(portal.center.toVector())
            val rightExtent = abs(portal.right.x) * halfX + abs(portal.right.y) * halfY + abs(portal.right.z) * halfZ
            val upExtent = abs(portal.up.x) * halfX + abs(portal.up.y) * halfY + abs(portal.up.z) * halfZ
            return abs(relative.dot(portal.right)) <= 0.9 + rightExtent &&
                abs(relative.dot(portal.up)) <= 1.36 + upExtent
        }

        fun tryTeleportCustomProjectile(location: Location, direction: Vector, distance: Double): Boolean {
            val blue = bluePortal ?: return false
            val orange = orangePortal ?: return false
            return tryTeleportRay(location, direction, distance, blue, orange) ||
                tryTeleportRay(location, direction, distance, orange, blue)
        }

        fun belongsTo(playerId: UUID): Boolean = player.uniqueId == playerId

        fun tryTeleportCollidedProjectile(projectile: BukkitProjectile): Boolean {
            val blue = bluePortal ?: return false
            val orange = orangePortal ?: return false
            val point = projectile.boundingBox.center
            val route = listOf(blue to orange, orange to blue).firstOrNull { (entry, _) ->
                if (projectile.world != entry.center.world) return@firstOrNull false
                val relative = point.clone().subtract(entry.center.toVector())
                abs(relative.dot(entry.normal)) <= 0.85 && insidePortal(entry, point, 0.2, 0.2, 0.2)
            } ?: return false
            val now = projectile.world.fullTime
            if (now < entityCooldowns.getOrDefault(projectile.uniqueId, Long.MIN_VALUE)) return false
            if (projectile.velocity.lengthSquared() < 1.0E-5) {
                val fallback = projectile.location.direction.normalize().multiply(0.65)
                if (fallback.dot(route.first.normal) > 0.0) fallback.multiply(-1.0)
                projectile.velocity = fallback
            }
            if (!teleportEntity(projectile, route.first, route.second)) return false
            entityCooldowns[projectile.uniqueId] = now + 1L
            previousEntityCenters[projectile.uniqueId] = projectile.boundingBox.center
            return true
        }

        private fun tryTeleportRay(
            location: Location,
            direction: Vector,
            distance: Double,
            entry: PortalData,
            exit: PortalData,
        ): Boolean {
            if (location.world != entry.center.world || distance <= 0.0) return false
            val denominator = direction.dot(entry.normal)
            if (denominator >= -1.0E-5) return false
            val triggerCenter = entry.center.toVector().add(entry.normal.clone().multiply(0.08))
            val relative = location.toVector().subtract(triggerCenter)
            val hitDistance = -relative.dot(entry.normal) / denominator
            if (hitDistance < -0.15 || hitDistance > distance + 0.2) return false
            val hitPoint = location.toVector().add(direction.clone().multiply(hitDistance.coerceAtLeast(0.0)))
            if (!insidePortal(entry, hitPoint)) return false
            val transformed = transformVector(entry, exit, direction).normalize()
            location.set(
                exit.center.x + exit.normal.x * 0.42,
                exit.center.y + exit.normal.y * 0.42,
                exit.center.z + exit.normal.z * 0.42,
            )
            location.direction = transformed
            direction.setX(transformed.x).setY(transformed.y).setZ(transformed.z)
            particles.spawn(entry.center, Particle.PORTAL, count = 10, spread = 0.35, speed = 0.06)
            particles.spawn(exit.center, Particle.REVERSE_PORTAL, count = 12, spread = 0.4, speed = 0.08)
            return true
        }

        private fun transformVector(entry: PortalData, exit: PortalData, vector: Vector): Vector {
            val localRight = vector.dot(entry.right)
            val localUp = vector.dot(entry.up)
            val localNormal = vector.dot(entry.normal)
            return exit.right.clone().multiply(localRight)
                .add(exit.up.clone().multiply(localUp))
                .add(exit.normal.clone().multiply(-localNormal))
        }

        fun clearPortals() {
            activePortalSkills.remove(this)
            allPortalSkills.remove(this)
            bluePortal?.display?.remove()
            orangePortal?.display?.remove()
            bluePortal = null
            orangePortal = null
            portalTask = null
            entityCooldowns.clear()
            previousEntityCenters.clear()
            portalMomentumByEntity.clear()
        }
    }

    companion object {
        private val activePortalSkills = mutableSetOf<RedSkill>()
        private val allPortalSkills = mutableSetOf<RedSkill>()

        fun teleportCustomProjectile(location: Location, direction: Vector, distance: Double): Boolean {
            return activePortalSkills.toList().any { it.tryTeleportCustomProjectile(location, direction, distance) }
        }

        fun teleportCollidedProjectile(projectile: BukkitProjectile): Boolean =
            activePortalSkills.toList().any { it.tryTeleportCollidedProjectile(projectile) }

        fun clearForPlayers(playerIds: Collection<UUID>) {
            allPortalSkills.toList()
                .filter { skill -> playerIds.any(skill::belongsTo) }
                .forEach { it.clearPortals() }
        }
    }
}
