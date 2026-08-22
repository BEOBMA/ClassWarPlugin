package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class AreaDevelopment : GameClass() {
    override val name = "<gray>영역전개"
    override val rank = Rank.S
    override val classItemMaterial = Material.BLACK_CONCRETE
    private val domainSkill = RedSkill()
    override var skills: List<Skill> = listOf(domainSkill)
    override var passives: List<BasePassive> = listOf()

    private inner class RedSkill : Skill(), OnHitHandler {
        override val name = "<bold>영역전개"
        override val description = listOf(
            "<gray>자신의 위치에 60초간 지름 20칸 크기의 영역을 전개한다.", "",
            "<gray>원래 영역 안에 있던 플레이어를 제외한 다른 플레이어는 접근할 수 없다.",
            "<gray>원래 영역 안에 있던 플레이어는 영역 밖으로 나갈 수 없다.",
            "<gray>영역 안의 플레이어는 영역 밖의 플레이어로부터 피해를 받지 않는다.",
            "<gray>영역 내에서 적에게 피해를 입히면 무작위 사선에서 사슬 하나가 내리꽂힌다.",
            "<gray>사슬에 실제로 적중한 적에게만 4의 피해를 입힌다.",
            "<gray>자신이 영역 내의 적을 하나라도 처치하면 영역이 파괴되며 영역 내 모든 적이 10의 피해를 입는다."
        )
        override val cooldown = 120

        private var center: Location? = null
        private var active = false
        private var domainTask: BukkitTask? = null
        private val boundaryStatuses = mutableListOf<DomainBoundaryStatus>()
        private val chainTargets = mutableSetOf<UUID>()
        private val allowedInsideIds = mutableSetOf<UUID>()

        override fun use() {
            finishDomain(collapse = false)
            val origin = player.location.clone()
            center = origin
            active = true
            activeDomains[player.uniqueId] = this
            val originallyInside = game.playerDatas.filterIsInstance<PlayerData>()
                .filter { isInside(origin, it.player.location) }
                .mapTo(mutableSetOf()) { it.uniqueId }
            allowedInsideIds.clear()
            allowedInsideIds.addAll(originallyInside)

            game.playerDatas.filterIsInstance<PlayerData>()
                .filter { it.player.isOnline && !it.entityStatus.isDead }
                .forEach { target ->
                    val status = DomainBoundaryStatus(
                        center = origin.clone(),
                        allowedInside = target.uniqueId in originallyInside,
                        radius = RADIUS,
                    )
                    target.addStatus(status, playerData)
                    status.applyStatus(duration = 60, powerSet = 1)
                    boundaryStatuses += status
                }

            game.playerDatas.filterIsInstance<PlayerData>()
                .filter { it.player.isOnline }
                .forEach { sounds.playTo(it.player, Sound.ENTITY_WITHER_SPAWN, volume = 0.55f, pitch = 0.55f) }
            playOpeningEffect(origin)

            var elapsedTicks = 0
            domainTask = playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (!active || !player.isOnline || playerStatus.isDead) {
                        finishDomain(collapse = false)
                        return
                    }
                    if (elapsedTicks >= 1200) {
                        finishDomain(collapse = false, naturalExpiration = true)
                        return
                    }
                    drawDomain(origin, elapsedTicks)
                    elapsedTicks += 2
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
        }

        override fun onHit(context: DamageContext) {
            val origin = center ?: return
            val targetCenter = context.target.entity.boundingBox.center.toLocation(origin.world)
            if (!active || context.target == playerData || !isInside(origin, targetCenter)) return
            val targetId = context.target.entity.uniqueId
            if (!chainTargets.add(targetId)) return
            launchFallingChain(context, origin, targetId)
        }

        private fun launchFallingChain(context: DamageContext, origin: Location, targetId: UUID) {
            val target = context.target.entity
            val initialBox = target.boundingBox
            val initialGround = initialBox.center.clone().also { it.y = initialBox.minY + 0.1 }.toLocation(target.world)
            val landingAngle = Random.nextDouble(0.0, 2.0 * PI)
            val landingRadius = Random.nextDouble(0.15, 2.15)
            val landing = initialGround.clone().add(
                cos(landingAngle) * landingRadius,
                0.0,
                sin(landingAngle) * landingRadius,
            )
            val approachAngle = Random.nextDouble(0.0, 2.0 * PI)
            val approachDistance = Random.nextDouble(4.8, 7.2)
            val skyOffset = Vector(
                cos(approachAngle) * approachDistance,
                Random.nextDouble(8.8, 11.5),
                sin(approachAngle) * approachDistance,
            )
            val sky = landing.clone().add(skyOffset)
            val direction = landing.toVector().subtract(sky.toVector()).normalize()
            val displays = List(CHAIN_LINK_COUNT) { link ->
                val location = sky.clone().subtract(direction.clone().multiply(link * CHAIN_LINK_SPACING))
                spawnChainLink(location, direction)
            }

            sounds.play(sky, Sound.BLOCK_CHAIN_PLACE, volume = 1.0f, pitch = 0.62f)
            playerData.trackTask(object : BukkitRunnable() {
                private var animationTick = 0
                private var damageApplied = false
                private var landed = false
                private var previousHead = sky.toVector()

                override fun run() {
                    if (!active || !target.isValid || target.isDead || animationTick > CHAIN_FALL_TICKS + CHAIN_HOLD_TICKS) {
                        stopAnimation()
                        return
                    }

                    val progress = (animationTick.toDouble() / CHAIN_FALL_TICKS).coerceIn(0.0, 1.0)
                    val remaining = 1.0 - progress
                    val easedProgress = 1.0 - remaining * remaining * remaining
                    val head = sky.clone().add(direction.clone().multiply(skyOffset.length() * easedProgress))
                    displays.forEachIndexed { link, display ->
                        display.teleport(head.clone().subtract(direction.clone().multiply(link * CHAIN_LINK_SPACING)))
                    }
                    if (animationTick < CHAIN_FALL_TICKS && animationTick % 2 == 0) {
                        particles.spawn(head, Particle.ELECTRIC_SPARK, count = 3, spread = 0.1, speed = 0.025)
                    }

                    val currentHead = head.toVector()
                    val hitCenter = target.boundingBox.center.toLocation(origin.world)
                    if (!damageApplied && isInside(origin, hitCenter) && HitboxUtil.intersectsSegment(
                            target.boundingBox,
                            previousHead,
                            currentHead,
                            expansion = CHAIN_HITBOX_EXPANSION,
                        )
                    ) {
                        damageApplied = true
                        context.target.damage(
                            4.0,
                            DamageType.Normal,
                            playerData,
                            damagePath = DamagePath.SKILL,
                        )
                        particles.spawn(head, Particle.CRIT, count = 18, spread = 0.32, speed = 0.1)
                        sounds.play(head, Sound.BLOCK_CHAIN_BREAK, volume = 0.9f, pitch = 0.82f)
                    }
                    previousHead = currentHead

                    if (!landed && animationTick >= CHAIN_FALL_TICKS) {
                        landed = true
                        particles.spawn(
                            landing,
                            Particle.BLOCK,
                            Material.IRON_CHAIN.createBlockData(),
                            ParticleOptions.spread(20, 0.34, 0.15),
                        )
                        particles.spawn(
                            landing,
                            if (damageApplied) Particle.CRIT else Particle.SMOKE,
                            count = if (damageApplied) 14 else 10,
                            spread = 0.3,
                            speed = 0.07,
                        )
                        sounds.play(landing, Sound.BLOCK_ANVIL_LAND, volume = 0.75f, pitch = 1.45f)
                        sounds.play(landing, Sound.BLOCK_CHAIN_PLACE, volume = 1.0f, pitch = 0.72f)
                    }
                    animationTick++
                }

                private fun stopAnimation() {
                    displays.forEach(BlockDisplay::remove)
                    chainTargets.remove(targetId)
                    cancel()
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }

        private fun spawnChainLink(location: Location, direction: Vector): BlockDisplay {
            val scale = Vector3f(1.22f, 1.12f, 1.22f)
            val rotation = Quaternionf().rotationTo(
                Vector3f(0f, 1f, 0f),
                Vector3f(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat()),
            )
            val rotatedCenter = rotation.transform(Vector3f(scale.x * 0.5f, scale.y * 0.5f, scale.z * 0.5f))
            return location.world.spawn(location, BlockDisplay::class.java).apply {
                block = Material.IRON_CHAIN.createBlockData()
                isPersistent = false
                brightness = Display.Brightness(12, 12)
                transformation = Transformation(
                    Vector3f(-rotatedCenter.x, -rotatedCenter.y, -rotatedCenter.z),
                    rotation,
                    scale,
                    Quaternionf(),
                )
                TemporaryDisplayManager.mark(this, player.uniqueId)
            }
        }

        fun contains(location: Location): Boolean = active && center?.let { isInside(it, location) } == true

        fun blocksCrossing(playerId: UUID, from: Location, to: Location): Boolean {
            val origin = center ?: return false
            if (!active) return false
            val wasInside = isInside(origin, from)
            val willBeInside = isInside(origin, to)
            return if (playerId in allowedInsideIds) {
                wasInside && !willBeInside
            } else {
                !wasInside && willBeInside
            }
        }

        fun finishDomain(collapse: Boolean, naturalExpiration: Boolean = false) {
            val origin = center ?: return
            if (!active && !collapse) return
            active = false
            activeDomains.remove(player.uniqueId, this)
            domainTask?.cancel()
            domainTask = null
            boundaryStatuses.toList().forEach(StatusAbnormality::remove)
            boundaryStatuses.clear()
            chainTargets.clear()
            allowedInsideIds.clear()

            if (collapse) {
                playCollapseEffect(origin)
                game.playerDatas.filterIsInstance<PlayerData>()
                    .filter { it != playerData && !it.entityStatus.isDead && containsIgnoringActive(origin, it.player.location) }
                    .forEach { target ->
                        target.damage(10.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                    }
            } else if (naturalExpiration) {
                playNaturalExpirationEffect(origin)
            } else {
                particles.circle(origin.clone().add(0.0, 0.3, 0.0), Particle.SMOKE, RADIUS, 36)
                sounds.play(origin, Sound.BLOCK_BEACON_DEACTIVATE, volume = 0.8f, pitch = 0.55f)
            }
            center = null
        }

        private fun playCollapseEffect(origin: Location) {
            sounds.play(origin, Sound.ENTITY_WITHER_DEATH, volume = 0.8f, pitch = 0.58f)
            sounds.play(origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 1.0f, pitch = 0.45f)
            particles.spawn(origin.clone().add(0.0, 2.2, 0.0), Particle.SONIC_BOOM, count = 1)

            var phase = 0
            playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (phase >= 8) {
                        particles.spawn(origin.clone().add(0.0, 1.2, 0.0), Particle.EXPLOSION_EMITTER, count = 5, spread = 2.8)
                        particles.spawn(origin.clone().add(0.0, 1.3, 0.0), Particle.SQUID_INK, count = 220, spread = 5.5, speed = 0.32)
                        particles.spawn(origin.clone().add(0.0, 1.5, 0.0), Particle.SCULK_SOUL, count = 110, spread = 4.0, speed = 0.18)
                        particles.spawn(
                            origin.clone().add(0.0, 1.1, 0.0),
                            Particle.BLOCK,
                            Material.BLACK_CONCRETE.createBlockData(),
                            ParticleOptions.spread(150, 3.4, 0.48),
                        )
                        drawDustRing(origin, RADIUS, 120, phase * 0.2, ARCANE_CYAN, 0.18)
                        sounds.play(origin, Sound.ENTITY_GENERIC_EXPLODE, volume = 1.45f, pitch = 0.4f)
                        sounds.play(origin, Sound.BLOCK_CHAIN_BREAK, volume = 1.2f, pitch = 0.5f)
                        cancel()
                        return
                    }

                    val progress = phase / 8.0
                    val radius = RADIUS * (1.0 - progress)
                    drawDustRing(origin, radius, 80, phase * 0.28, DEEP_PURPLE, 0.16 + progress * 1.1)
                    drawDustRing(origin, radius * 0.72, 56, -phase * 0.35, ARCANE_CYAN, 0.3 + progress * 1.4)
                    repeat(36) { index ->
                        val angle = 2.0 * PI * index / 36.0 + phase * 0.2
                        val y = 5.5 * (1.0 - progress) + sin(index * 0.85 + phase) * 0.45
                        spawnDust(
                            origin.clone().add(cos(angle) * radius, y, sin(angle) * radius),
                            if (index % 3 == 0) VOID_BLACK else DEEP_PURPLE,
                            1.35f,
                        )
                    }
                    particles.spawn(origin.clone().add(0.0, 1.2, 0.0), Particle.REVERSE_PORTAL, count = 28, spread = radius * 0.35, speed = 0.09)
                    if (phase == 4) sounds.play(origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 1.15f, pitch = 0.7f)
                    phase++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
        }

        private fun playNaturalExpirationEffect(origin: Location) {
            sounds.play(origin, Sound.BLOCK_BEACON_DEACTIVATE, volume = 1.0f, pitch = 0.7f)
            sounds.play(origin, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.8f, pitch = 0.85f)

            var phase = 0
            playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (phase > 10) {
                        particles.spawn(origin.clone().add(0.0, 5.5, 0.0), Particle.REVERSE_PORTAL, count = 150, spread = 5.0, speed = 0.08)
                        particles.spawn(origin.clone().add(0.0, 4.8, 0.0), Particle.SCULK_SOUL, count = 90, spread = 3.8, speed = 0.1)
                        particles.spawn(origin.clone().add(0.0, 5.2, 0.0), Particle.END_ROD, count = 65, spread = 4.2, speed = 0.05)
                        sounds.play(origin, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, volume = 0.9f, pitch = 0.7f)
                        cancel()
                        return
                    }

                    val progress = phase / 10.0
                    val height = 0.25 + progress * 5.6
                    val radius = RADIUS * (1.0 - progress * 0.28)
                    drawDustRing(origin, radius, 84, phase * 0.12, DEEP_PURPLE, height)
                    drawDustRing(origin, radius * 0.7, 60, -phase * 0.18, ARCANE_CYAN, height + 0.2)
                    repeat(24) { index ->
                        val angle = 2.0 * PI * index / 24.0 + phase * 0.16
                        val point = origin.clone().add(cos(angle) * radius, height + sin(index * 0.8) * 0.35, sin(angle) * radius)
                        particles.spawn(point, if (index % 3 == 0) Particle.END_ROD else Particle.ENCHANT, count = 1)
                    }
                    particles.spawn(origin.clone().add(0.0, height, 0.0), Particle.WITCH, count = 26, spread = radius * 0.45, speed = 0.025)
                    if (phase == 3 || phase == 7) {
                        sounds.play(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.75f, pitch = 1.2f - phase * 0.05f)
                    }
                    phase++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
        }

        private fun playOpeningEffect(origin: Location) {
            particles.spawn(origin.clone().add(0.0, 1.0, 0.0), Particle.REVERSE_PORTAL, count = 220, spread = 4.8, speed = 0.26)
            particles.spawn(origin.clone().add(0.0, 0.7, 0.0), Particle.SQUID_INK, count = 110, spread = 3.0, speed = 0.18)
            particles.spawn(origin.clone().add(0.0, 1.4, 0.0), Particle.SCULK_SOUL, count = 52, spread = 1.8, speed = 0.1)

            repeat(4) { ring ->
                drawDustRing(
                    origin = origin,
                    radius = RADIUS * (ring + 1) / 4.0,
                    points = 40 + ring * 16,
                    phase = ring * PI / 8.0,
                    color = if (ring % 2 == 0) DEEP_PURPLE else ARCANE_CYAN,
                    y = 0.12 + ring * 0.025,
                )
            }
            sounds.play(origin, Sound.BLOCK_END_PORTAL_SPAWN, volume = 0.55f, pitch = 0.65f)
        }

        private fun drawDomain(origin: Location, tick: Int) {
            drawBoundaryCurtain(origin, tick)
            drawCentralCore(origin, tick)

            if (tick % 4 == 0) {
                drawGroundSigil(origin, tick)
                drawCrown(origin, tick)
                drawAerialRunes(origin, tick)
            }
            if (tick % 10 == 0) {
                particles.circle(origin.clone().add(0.0, 0.16, 0.0), Particle.SOUL_FIRE_FLAME, RADIUS, 80)
                particles.circle(origin.clone().add(0.0, 0.22, 0.0), Particle.WITCH, RADIUS * 0.72, 52)
                particles.circle(origin.clone().add(0.0, 3.1, 0.0), Particle.REVERSE_PORTAL, RADIUS * 0.46, 32)
            }
        }

        private fun drawBoundaryCurtain(origin: Location, tick: Int) {
            repeat(76) { index ->
                val angle = 2.0 * PI * index / 76.0 + tick * 0.012
                val wave = (sin(index * 1.37 + tick * 0.11) + 1.0) * 0.5
                val radius = RADIUS + sin(index * 0.73 - tick * 0.05) * 0.1
                val y = 0.25 + wave * 5.2
                val color = when (index % 4) {
                    0 -> ARCANE_CYAN
                    2 -> VOID_BLACK
                    else -> DEEP_PURPLE
                }
                spawnDust(origin.clone().add(cos(angle) * radius, y, sin(angle) * radius), color, 1.25f)
            }

            repeat(16) { index ->
                val angle = 2.0 * PI * index / 16.0 - tick * 0.008
                val y = 0.4 + (tick * 0.13 + index * 0.72) % 5.4
                val point = origin.clone().add(cos(angle) * RADIUS, y, sin(angle) * RADIUS)
                particles.spawn(point, Particle.SCULK_SOUL, count = 1)
            }
        }

        private fun drawGroundSigil(origin: Location, tick: Int) {
            val rotation = tick * 0.014
            drawDustRing(origin, RADIUS - 0.15, 76, rotation, DEEP_PURPLE, 0.1)
            drawDustRing(origin, RADIUS * 0.7, 58, -rotation * 1.35, ARCANE_CYAN, 0.11)
            drawDustRing(origin, RADIUS * 0.29, 40, rotation * 2.0, VOID_BLACK, 0.13)

            val vertices = List(16) { index ->
                val angle = 2.0 * PI * index / 16.0 + rotation
                val radius = if (index % 2 == 0) RADIUS * 0.78 else RADIUS * 0.38
                origin.clone().add(cos(angle) * radius, 0.14, sin(angle) * radius)
            }
            repeat(vertices.size) { index ->
                drawDottedLine(
                    vertices[index],
                    vertices[(index + 1) % vertices.size],
                    if (index % 2 == 0) ARCANE_CYAN else DEEP_PURPLE,
                    points = 5,
                )
            }

            repeat(16) { index ->
                val angle = 2.0 * PI * index / 16.0 - rotation * 0.65
                val node = origin.clone().add(cos(angle) * RADIUS * 0.56, 0.18, sin(angle) * RADIUS * 0.56)
                particles.spawn(node, Particle.ENCHANT, count = 1)
            }
        }

        private fun drawCentralCore(origin: Location, tick: Int) {
            repeat(14) { index ->
                val y = 0.3 + index * 0.26
                val angle = tick * 0.09 + index * 0.58
                val radius = 0.62 + sin(tick * 0.04 + index) * 0.08
                spawnDust(origin.clone().add(cos(angle) * radius, y, sin(angle) * radius), ARCANE_CYAN, 1.1f)
                spawnDust(origin.clone().add(-cos(angle) * radius, y, -sin(angle) * radius), DEEP_PURPLE, 1.2f)
            }
            particles.spawn(
                origin.clone().add(0.0, 1.8, 0.0),
                Particle.REVERSE_PORTAL,
                count = 4,
                spread = 0.42,
                speed = 0.015,
            )
        }

        private fun drawCrown(origin: Location, tick: Int) {
            repeat(28) { index ->
                val angle = 2.0 * PI * index / 28.0 - tick * 0.01
                val radius = RADIUS * (0.82 + 0.08 * sin(index * 1.7 + tick * 0.06))
                val y = 5.25 + sin(index * 0.9 + tick * 0.08) * 0.55
                val point = origin.clone().add(cos(angle) * radius, y, sin(angle) * radius)
                spawnDust(point, if (index % 2 == 0) DEEP_PURPLE else ARCANE_CYAN, 1.1f)
                if (index % 2 == 0) particles.spawn(point, Particle.END_ROD, count = 1)
            }
        }

        private fun drawAerialRunes(origin: Location, tick: Int) {
            repeat(36) { index ->
                val orbit = index % 3
                val radius = 3.2 + orbit * 1.65
                val angle = 2.0 * PI * index / 36.0 + tick * (0.018 + orbit * 0.004)
                val y = 1.4 + orbit * 0.8 + sin(index * 1.15 + tick * 0.07) * 0.32
                val point = origin.clone().add(cos(angle) * radius, y, sin(angle) * radius)
                spawnDust(point, if ((index + orbit) % 2 == 0) ARCANE_CYAN else DEEP_PURPLE, 0.95f)
                if (index % 6 == 0) particles.spawn(point, Particle.WITCH, count = 1)
            }
        }

        private fun drawDustRing(
            origin: Location,
            radius: Double,
            points: Int,
            phase: Double,
            color: Color,
            y: Double,
        ) {
            repeat(points) { index ->
                val angle = 2.0 * PI * index / points + phase
                spawnDust(origin.clone().add(cos(angle) * radius, y, sin(angle) * radius), color, 1.05f)
            }
        }

        private fun drawDottedLine(from: Location, to: Location, color: Color, points: Int) {
            val step = to.toVector().subtract(from.toVector()).multiply(1.0 / points)
            val point = from.clone()
            repeat(points) {
                spawnDust(point, color, 0.9f)
                point.add(step)
            }
        }

        private fun spawnDust(location: Location, color: Color, size: Float) {
            particles.spawn(
                location,
                Particle.DUST,
                Particle.DustOptions(color, size),
                ParticleOptions(count = 1),
            )
        }
    }

    companion object {
        private const val RADIUS = 10.0
        private const val CHAIN_LINK_COUNT = 16
        private const val CHAIN_LINK_SPACING = 0.92
        private const val CHAIN_HITBOX_EXPANSION = 0.5
        private const val CHAIN_FALL_TICKS = 9
        private const val CHAIN_HOLD_TICKS = 5
        private val DEEP_PURPLE = Color.fromRGB(92, 12, 150)
        private val ARCANE_CYAN = Color.fromRGB(45, 210, 225)
        private val VOID_BLACK = Color.fromRGB(12, 2, 20)
        private val activeDomains = mutableMapOf<UUID, RedSkill>()

        private fun horizontalDistanceSquared(a: Location, b: Location): Double {
            if (a.world != b.world) return Double.POSITIVE_INFINITY
            val dx = a.x - b.x
            val dz = a.z - b.z
            return dx * dx + dz * dz
        }

        private fun isInside(center: Location, location: Location): Boolean =
            horizontalDistanceSquared(center, location) <= RADIUS * RADIUS

        private fun containsIgnoringActive(center: Location, location: Location): Boolean = isInside(center, location)

        fun handlePlayerDeath(victim: PlayerData, killerId: UUID?) {
            val deathCenter = victim.player.boundingBox.center.toLocation(victim.player.world)
            handleEntityDeath(victim.uniqueId, deathCenter, killerId)
        }

        fun handleEntityDeath(victimId: UUID, location: Location, killerId: UUID?) {
            if (killerId == null || killerId == victimId) return
            val domain = activeDomains[killerId] ?: return
            if (domain.contains(location)) domain.finishDomain(collapse = true)
        }

        fun clearDomains(playerIds: Collection<UUID>) {
            playerIds.mapNotNull { activeDomains[it] }.toSet()
                .forEach { it.finishDomain(collapse = false) }
        }

        fun shouldBlockTeleport(playerId: UUID, from: Location, to: Location): Boolean =
            activeDomains.values.any { it.blocksCrossing(playerId, from, to) }
    }
}

private class DomainBoundaryStatus(
    private val center: Location,
    private val allowedInside: Boolean,
    private val radius: Double,
) : StatusAbnormality(), WhenHitHandler, StatusPlayerMoveHandler {
    override val name = "<dark_purple><bold>영역 경계</bold><gray>"
    override val description = listOf("<gray>영역의 진입·이탈 및 외부 피해를 차단한다.")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = 60

    override fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData) {
        if (event.to.world != center.world) return
        val wasInside = inside(event.from)
        val willBeInside = inside(event.to)
        val crossedBoundary = if (allowedInside) wasInside && !willBeInside else !wasInside && willBeInside
        if (crossedBoundary) {
            event.isCancelled = true
            var pushDirection = if (allowedInside) {
                center.toVector().subtract(event.player.location.toVector())
            } else {
                event.player.location.toVector().subtract(center.toVector())
            }.setY(0.15)
            if (pushDirection.lengthSquared() < 1.0E-8) pushDirection = Vector(1.0, 0.15, 0.0)
            event.player.velocity = pushDirection.normalize().multiply(0.35)
            event.player.spawnParticle(Particle.SMOKE, event.player.location.add(0.0, 1.0, 0.0), 8, 0.25, 0.4, 0.25, 0.02)
            event.player.playSound(event.player.location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.35f, 1.65f)
        }
    }

    override fun whenHit(context: DamageContext) {
        if (!inside(context.target.entity.location)) return
        if (!inside(context.attacker.player.location)) context.isCancelled = true
    }

    private fun inside(location: Location): Boolean {
        if (location.world != center.world) return false
        val dx = location.x - center.x
        val dz = location.z - center.z
        return dx * dx + dz * dz <= radius * radius
    }
}
