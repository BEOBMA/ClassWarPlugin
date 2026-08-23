package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.MovementInputHandler
import org.beobma.classWarPlugin.gameClass.handler.SneakInputHandler
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import org.bukkit.util.BoundingBox
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import kotlin.math.max
import kotlin.math.min

// 밸런스 조정 상수
private const val ASSASSIN_DAGGER_COOLDOWN_SECONDS = 30
private const val ASSASSIN_STEALTH_DURATION_SECONDS = 6
private const val ASSASSIN_DAGGER_DAMAGE = 5.0
private const val ASSASSIN_BACKSTAB_BONUS_DAMAGE = 2.0

class Assassin : GameClass(), EnvironmentalDamageHandler, StatusPlayerMoveHandler, SneakInputHandler,
    MovementInputHandler {
    override val name = "<gray>암살자"
    override val rank = Rank.B
    override val classItemMaterial = Material.NETHERITE_HELMET
    override val weapon: BaseWeapon = Weapon()

    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private var wallAttached = false
    private var wallAnchor: Location? = null
    private var wallSupportBlock: Block? = null
    private var wallMovementInputArmed = true
    private var wallHoldTask: BukkitTask? = null
    private var embeddedDaggerDisplay: ItemDisplay? = null
    private var embeddedDaggerTask: BukkitTask? = null
    private var ignoreNextFallDamage = false

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL && ignoreNextFallDamage) {
            event.isCancelled = true
            ignoreNextFallDamage = false
            player.fallDistance = 0f
        }
    }

    override fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData) {
        if (!wallAttached) return
        val movement = event.to.toVector().subtract(event.from.toVector())
        // 충돌 보정이나 다른 엔티티의 밀침은 이탈 입력으로 취급하지 않는다.
        // 실제 WASD/점프 입력은 PlayerInputEvent에서 별도로 처리한다.
        if (movement.lengthSquared() >= 1.0E-8) lockToWallAnchor(event)
    }

    override fun onPlayerInput(event: PlayerInputEvent) {
        if (!wallAttached) return
        val hasMovementInput = event.input.run { isForward || isBackward || isLeft || isRight || isJump }
        if (!hasMovementInput) {
            wallMovementInputArmed = true
            return
        }
        // 벽에 닿기 전부터 누르고 있던 키 때문에 즉시 떨어지는 것을 막는다.
        if (!wallMovementInputArmed) return
        detachFromWall(keepStealth = true, playSound = true)
    }

    override fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking || !wallAttached) return
        detachFromWall(keepStealth = true, playSound = true)
        player.velocity = Vector(0.0, -0.2, 0.0)
    }

    private fun lockToWallAnchor(event: PlayerMoveEvent) {
        val anchor = wallAnchor ?: return
        val look = event.to
        event.to = anchor.clone().apply {
            yaw = look.yaw
            pitch = look.pitch
        }
    }

    private fun detachFromWall(keepStealth: Boolean, playSound: Boolean) {
        if (!wallAttached) return
        wallAttached = false
        wallAnchor = null
        wallSupportBlock = null
        wallMovementInputArmed = true
        wallHoldTask?.cancel()
        wallHoldTask = null
        player.setGravity(true)
        player.velocity = Vector(0, 0, 0)
        CooldownManager.resumeCooldown(player, skills.first())

        val stealth = playerData.getStatus<Stealth>()
        if (keepStealth) {
            stealth?.continueWhile = null
            (stealth ?: playerData.getOrCreateStatus(playerData) { Stealth() })
                .applyStatus(duration = ASSASSIN_STEALTH_DURATION_SECONDS, powerSet = 1)
        } else {
            stealth?.remove()
            removeEmbeddedDagger()
        }
        if (playSound) sounds.play(player, Sound.BLOCK_CHAIN_BREAK, pitch = 1.3f)
    }

    private fun findSafeWallAnchor(wallSurface: Location, outward: Vector): Location? {
        if (outward.lengthSquared() < 1.0E-8) return null
        val normal = outward.clone().normalize()
        val width = max(player.width, 0.6)
        val height = max(player.height, 1.8)
        val halfWidth = width / 2.0
        val clearance = 0.08
        val base = wallSurface.clone()

        when {
            normal.y > 0.5 -> base.y += clearance
            normal.y < -0.5 -> base.y -= height + clearance
            else -> {
                base.add(normal.clone().multiply(halfWidth + clearance))
                base.y -= height / 2.0
            }
        }

        val tangentA = if (kotlin.math.abs(normal.y) < 0.9) {
            Vector(-normal.z, 0.0, normal.x).normalize()
        } else {
            Vector(1.0, 0.0, 0.0)
        }
        val tangentB = normal.clone().crossProduct(tangentA).normalize()
        val candidates = buildList {
            add(base.clone())
            for (distance in listOf(0.25, 0.5, 0.75, 1.0)) {
                add(base.clone().add(tangentB.clone().multiply(distance)))
                add(base.clone().add(tangentB.clone().multiply(-distance)))
                if (distance <= 0.5) {
                    add(base.clone().add(tangentA.clone().multiply(distance)))
                    add(base.clone().add(tangentA.clone().multiply(-distance)))
                }
            }
        }

        return candidates.firstOrNull { candidate ->
            val box = BoundingBox(
                candidate.x - halfWidth,
                candidate.y,
                candidate.z - halfWidth,
                candidate.x + halfWidth,
                candidate.y + height,
                candidate.z + halfWidth,
            )
            !player.wouldCollideUsing(box)
        }
    }

    private fun placeEmbeddedDagger(wallSurface: Location, outward: Vector, bladeDirection: Vector) {
        removeEmbeddedDagger()
        val displayLocation = wallSurface.clone().add(outward.clone().normalize().multiply(0.35))
        val display = displayLocation.world.spawn(displayLocation, ItemDisplay::class.java)
        display.setItemStack(ItemStack(Material.IRON_SWORD))
        TemporaryDisplayManager.mark(display, player.uniqueId)
        DisplayOrientationUtil.alignSwordBladeVertically(display, bladeDirection, scale = 1.45f)
        embeddedDaggerDisplay = display
        embeddedDaggerTask = playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (!display.isValid || !playerData.hasStatus<Stealth>()) {
                    removeEmbeddedDagger()
                    cancel()
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
    }

    private fun removeEmbeddedDagger() {
        embeddedDaggerDisplay?.remove()
        embeddedDaggerDisplay = null
        embeddedDaggerTask?.cancel()
        embeddedDaggerTask = null
    }


    private class Weapon : BaseWeapon() {
        override val name = "단검"
        override val description = listOf("")
        override val material = Material.IRON_SWORD
    }

    private inner class RedSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val name = "<bold>단검 투척"
        override val description = listOf(
            "<gray>바라보는 방향으로 단검을 투척한다.",
            "",
            "<gray>단검이 적에게 적중하면 5의 피해를 입히고 해당 적의 뒤로 즉시 이동한다.",
            "<gray>단검이 블록에 적중하면 {keyword:Stealth} 상태가 되고, 해당 위치로 날아가 최대 10초간 벽에 붙는다.",
            "<gray>벽에 박힌 단검은 {keyword:Stealth} 효과가 사라질 때 같이 사라진다.",
            "<gray>벽에 붙은 상태에서 이동하거나 웅크리면 벽에서 떨어진다.",
            "",
            "<dark_gray>이 스킬을 사용한 후, 최초 1회의 낙하 피해는 무효화되며, 벽에서 떨어진 후 6초간 {keyword:Stealth}<dark_gray> 상태가 유지된다.",
        )
        override val cooldown = ASSASSIN_DAGGER_COOLDOWN_SECONDS

        override fun use() {
            ignoreNextFallDamage = true
            val assassinsDaggerProjectile = DaggerProjectile()
            assassinsDaggerProjectile.location = player.eyeLocation.clone()
            assassinsDaggerProjectile.spawnProjectile(playerData)
            sounds.play(player, Sound.ENTITY_SKELETON_SHOOT, pitch = 2f)
        }
    }

    private class Passive : BasePassive(), OnHitHandler {
        override val name = "<bold>암살"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시, 대상이 자신을 바라보고 있지 않았다면 피해량이 2 증가한다."
        )

        override fun onAttackHit(context: DamageContext) {
            val target = context.target.entity
            val toAssassin = player.location.toVector().subtract(target.location.toVector()).normalize()
            val targetFacing = target.location.direction.normalize()
            if (targetFacing.dot(toAssassin) < 0.0) {
                context.addBaseDamage(ASSASSIN_BACKSTAB_BONUS_DAMAGE)
                particles.spawn(target, Particle.CRIT, count = 10, spread = 0.25)
                sounds.play(target, Sound.ENTITY_PLAYER_ATTACK_CRIT, pitch = 1.25f)
            }
        }
    }

    private inner class DaggerProjectile : Projectile() {
        override lateinit var location: Location
        override var targetType: TargetType = TargetType.Enemy
        override var speed: Double = 1.0
        override var isWallHit: Boolean = true
        override var isPlayerHit: Boolean = true
        override val isPlayerHitRemove: Boolean = true
        override var time: Int? = 5
        override val itemDisplayItem: ItemStack = ItemStack(Material.IRON_SWORD)
        private lateinit var flightOrigin: Location
        private lateinit var flightDirection: Vector

        override fun onItemDisplaySpawn(display: ItemDisplay, location: Location) {
            flightOrigin = location.clone()
            flightDirection = location.direction.normalize()
            DisplayOrientationUtil.alignSwordBladeVertically(display, flightDirection, scale = 1.45f)
        }

        override fun onItemDisplayMove(display: ItemDisplay, location: Location, speed: Double, tick: Int) {
            DisplayOrientationUtil.alignSwordBladeVertically(display, flightDirection, scale = 1.45f)
        }

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            val hitEntity = hitEntityData.entity
            val hitEntityLocation = hitEntity.location
            val behind = hitEntityLocation.clone().add(hitEntityLocation.direction.normalize().multiply(-1.5))
            hitEntityData.damage(ASSASSIN_DAGGER_DAMAGE, DamageType.Normal, playerData)
            particles.spawn(player.location, Particle.SMOKE, count = 10)
            particles.spawn(behind, Particle.SMOKE, count = 10)
            player.teleport(behind)
            sounds.play(player, Sound.ITEM_TRIDENT_RETURN)
        }

        override fun onProjectileBlockHit(hitBlock: Block, location: Location) {
            val hitPoint = location.clone()
            sounds.play(hitPoint, Sound.BLOCK_ANVIL_LAND, volume = 0.65f, pitch = 1.75f)
            sounds.play(hitPoint, Sound.ITEM_TRIDENT_HIT_GROUND, volume = 0.85f, pitch = 1.4f)

            // 현재 투사체 위치는 이미 블록 내부일 수 있으므로 최초 발사점에서 다시 ray trace한다.
            val blockHit = player.world.rayTraceBlocks(
                flightOrigin,
                flightDirection,
                flightOrigin.distance(hitPoint) + 2.0,
                FluidCollisionMode.NEVER,
                true,
            )
            val supportBlock = blockHit?.hitBlock ?: hitBlock
            val outward = blockHit?.hitBlockFace?.direction ?: fallbackOutwardDirection()
            val wallSurface = blockHit?.hitPosition?.toLocation(player.world)
                ?: fallbackWallSurface(supportBlock, hitPoint, outward)

            val stealth = playerData.getOrCreateStatus(playerData) { Stealth() }
            stealth.applyStatus(
                duration = ASSASSIN_STEALTH_DURATION_SECONDS,
                powerDelta = 1
            )
            placeEmbeddedDagger(wallSurface, outward, flightDirection)

            val target = findSafeWallAnchor(wallSurface, outward)
            if (target == null) {
                player.sendMiniMessage("<red><bold>[!] 몸이 들어갈 안전한 공간이 없어 벽에 붙지 못했습니다.")
                return
            }

            val durationTicks = 20
            val stopDistance = 0.15

            val minSpeed = 0.20
            val maxSpeedCap = 3.50

            val maxUp = 0.75
            val maxDown = 1.10
            val minUpWhenTargetAbove = 0.28

            val startVec = player.location.toVector()
            val totalDist = target.toVector().subtract(startVec).length()

            val basePerTickSpeed = totalDist / durationTicks.toDouble()

            val perTickSpeed = max(minSpeed, basePerTickSpeed).coerceAtMost(maxSpeedCap)

            val originalGravity = player.hasGravity()
            player.setGravity(false)

            val task = object : BukkitRunnable() {
                var ticks = 0

                private fun attachToWall() {
                    // 날아오는 동안 주변 블록이 바뀌었을 수 있으므로 부착 직전에 다시 검증한다.
                    val safeAnchor = findSafeWallAnchor(wallSurface, outward)
                    if (safeAnchor == null) {
                        stop()
                        return
                    }
                    val anchor = safeAnchor.apply {
                        yaw = player.location.yaw
                        pitch = player.location.pitch
                    }
                    player.teleport(anchor)
                    player.velocity = Vector(0, 0, 0)
                    player.setGravity(false)
                    wallAttached = true
                    wallAnchor = anchor.clone()
                    wallSupportBlock = supportBlock
                    wallMovementInputArmed = !player.currentInput.run {
                        isForward || isBackward || isLeft || isRight || isJump
                    }
                    playerData.getOrCreateStatus(playerData) { Stealth() }.apply {
                        updatePower(1)
                        setContinueWhileIf { wallAttached }
                    }
                    CooldownManager.pauseCooldown(player, skills.first())
                    wallHoldTask?.cancel()
                    wallHoldTask = playerData.trackTask(object : BukkitRunnable() {
                        var attachedTicks = 0

                        override fun run() {
                            if (!wallAttached || !player.isOnline || player.isDead) {
                                detachFromWall(keepStealth = false, playSound = false)
                                cancel()
                                return
                            }
                            if (wallSupportBlock?.type?.isSolid != true) {
                                detachFromWall(keepStealth = true, playSound = true)
                                cancel()
                                return
                            }
                            if (++attachedTicks >= 10 * 20) {
                                detachFromWall(keepStealth = true, playSound = true)
                                cancel()
                                return
                            }
                            val currentAnchor = wallAnchor
                            if (currentAnchor != null &&
                                player.location.toVector().distanceSquared(currentAnchor.toVector()) > 1.0E-6
                            ) {
                                val look = player.location
                                player.teleport(currentAnchor.clone().apply {
                                    yaw = look.yaw
                                    pitch = look.pitch
                                })
                            }
                            player.setGravity(false)
                            player.velocity = Vector(0, 0, 0)
                            player.fallDistance = 0f
                            CooldownManager.refreshPlayer(player)
                        }
                    }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
                    sounds.play(player, Sound.BLOCK_CHAIN_PLACE, volume = 1.0f, pitch = 1.4f)
                    cancel()
                }

                private fun stop() {
                    player.velocity = Vector(0, 0, 0)
                    player.setGravity(originalGravity)
                    cancel()
                }

                override fun run() {
                    if (!player.isOnline || player.isDead) {
                        stop()
                        return
                    }

                    if (++ticks > durationTicks) {
                        attachToWall()
                        return
                    }

                    val delta = target.toVector().subtract(player.location.toVector())
                    val dist = delta.length()

                    if (dist <= stopDistance) {
                        attachToWall()
                        return
                    }

                    val speedThisTick = min(dist, perTickSpeed)
                    val vel = delta.normalize().multiply(speedThisTick)

                    if (delta.y > 0.2) {
                        vel.y = max(vel.y, minUpWhenTargetAbove)
                    }
                    vel.y = vel.y.coerceIn(-maxDown, maxUp)

                    val y = vel.y
                    val maxXz = kotlin.math.sqrt(max(0.0, speedThisTick * speedThisTick - y * y))
                    val xz = Vector(vel.x, 0.0, vel.z)
                    val xzLen = xz.length()
                    if (xzLen > 1.0E-6) {
                        xz.multiply(maxXz / xzLen)
                        vel.x = xz.x
                        vel.z = xz.z
                    }

                    player.velocity = vel
                    player.fallDistance = 0f
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)

            playerData.trackTask(task)
        }

        private fun fallbackOutwardDirection(): Vector {
            val reverse = flightDirection.clone().multiply(-1.0)
            val absX = kotlin.math.abs(reverse.x)
            val absY = kotlin.math.abs(reverse.y)
            val absZ = kotlin.math.abs(reverse.z)
            return when {
                absX >= absY && absX >= absZ -> Vector(if (reverse.x >= 0.0) 1.0 else -1.0, 0.0, 0.0)
                absY >= absZ -> Vector(0.0, if (reverse.y >= 0.0) 1.0 else -1.0, 0.0)
                else -> Vector(0.0, 0.0, if (reverse.z >= 0.0) 1.0 else -1.0)
            }
        }

        private fun fallbackWallSurface(block: Block, hitPoint: Location, outward: Vector): Location {
            val minX = block.x.toDouble()
            val minY = block.y.toDouble()
            val minZ = block.z.toDouble()
            val surface = Location(
                block.world,
                hitPoint.x.coerceIn(minX, minX + 1.0),
                hitPoint.y.coerceIn(minY, minY + 1.0),
                hitPoint.z.coerceIn(minZ, minZ + 1.0),
            )
            when {
                outward.x > 0.5 -> surface.x = minX + 1.0
                outward.x < -0.5 -> surface.x = minX
                outward.y > 0.5 -> surface.y = minY + 1.0
                outward.y < -0.5 -> surface.y = minY
                outward.z > 0.5 -> surface.z = minZ + 1.0
                outward.z < -0.5 -> surface.z = minZ
            }
            return surface
        }
    }
}
