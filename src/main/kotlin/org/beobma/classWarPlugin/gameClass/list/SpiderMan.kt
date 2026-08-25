package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.MovementInputHandler
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.AttackableObjectManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.SpiderWebChargeStatus
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.status.list.Snare
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val SPIDER_MAN_WEB_COOLDOWN_SECONDS = 6
private const val SPIDER_MAN_MAX_WEB_CHARGES = 5
private const val SPIDER_MAN_RECHARGE_TICKS = 120

class SpiderMan : GameClass(), GameStatusHandler, GameEndHandler, MovementInputHandler, EnvironmentalDamageHandler {
    private val webSkill = RedSkill()

    override val name = "<gray>스파이더맨"
    override val rank = Rank.A
    override val classItemMaterial = Material.SPIDER_SPAWN_EGG
    override var skills: List<Skill> = listOf(webSkill)
    override var passives: List<BasePassive> = listOf()

    override fun onBattleStart() = webSkill.initialize()

    override fun onGameTimePasses() = webSkill.initialize()

    override fun onGameEnd() = webSkill.shutdown()

    override fun onPlayerInput(event: PlayerInputEvent) = webSkill.updateMovementInput(event)

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL || !webSkill.isWebConnected()) return
        event.isCancelled = true
        player.fallDistance = 0f
    }

    private class RedSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val name = "<bold>거미줄"
        override val description = listOf(
            "<gray>최대 5회 충전되는 충전형 스킬.",
            "",
            "<gray>바라보는 방향으로 거미줄을 발사한다.",
            "<gray>거미줄이 블럭에 닿으면 해당 블럭을 중심으로 원호를 그리며 공중을 이동한다.",
            "<gray>이동키로 가속 방향을 조절하고, 점프키를 누르면 로프를 감아 빠르게 상승한다.",
            "<gray>웅크리면 거미줄을 놓으며 현재 이동 방향과 속도를 유지한 채 날아간다.",
            "",
            "<gray>거미줄이 플레이어에게 닿으면 해당 플레이어에게 날아가며 이 스킬의 충전은 0이 된다.",
            "<gray>침묵 또는 속박 상태가 되거나, 거미줄이 적의 기본 공격, 스킬 또는 투사체에 맞으면 연결이 끊어진다.",
            "",
            "<dark_gray>이 스킬을 사용하는 중에는 재사용 대기 시간이 감소하지 않는다."
        )
        override val cooldown = SPIDER_MAN_WEB_COOLDOWN_SECONDS

        private var initialized = false
        private var rechargeTask: BukkitTask? = null
        private var swingTask: BukkitTask? = null
        private var rechargeProgressTicks = 0
        private var webInFlight = false
        private var swinging = false
        private var forwardInput = false
        private var backwardInput = false
        private var leftInput = false
        private var rightInput = false
        private var jumpInput = false
        private var breakRequested = false
        private var webStart: Location? = null
        private var webEnd: Location? = null
        private var webPullsToEntity = false
        private var webTargetRegistration: AttackableObjectManager.Registration? = null
        private var activeAnchorDisplay: BlockDisplay? = null
        private var swingOriginalGravity: Boolean? = null

        fun isWebConnected(): Boolean = swinging

        fun updateMovementInput(event: PlayerInputEvent) {
            forwardInput = event.input.isForward
            backwardInput = event.input.isBackward
            leftInput = event.input.isLeft
            rightInput = event.input.isRight
            jumpInput = event.input.isJump
        }

        fun initialize() {
            if (!initialized) {
                initialized = true
                val status = playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }
                status.updateState(SPIDER_MAN_MAX_WEB_CHARGES, 0)
            }
            ensureRechargeTask()
        }

        fun shutdown() {
            breakRequested = true
            rechargeTask?.cancel()
            rechargeTask = null
            swingTask?.cancel()
            swingTask = null
            activeAnchorDisplay?.remove()
            activeAnchorDisplay = null
            swingOriginalGravity?.let(player::setGravity)
            swingOriginalGravity = null
            webInFlight = false
            swinging = false
            clearWebTarget()
            playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }.setRopeState(null)
            initialized = false
        }

        private fun ensureRechargeTask() {
            if (rechargeTask != null) return
            rechargeTask = playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline || player.isDead) {
                        rechargeTask = null
                        cancel()
                        return
                    }
                    val status = playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }
                    if (webInFlight || swinging) {
                        status.updateState(status.power, (SPIDER_MAN_RECHARGE_TICKS - rechargeProgressTicks).coerceAtLeast(0))
                        return
                    }
                    if (status.power >= SPIDER_MAN_MAX_WEB_CHARGES) {
                        rechargeProgressTicks = 0
                        status.updateState(SPIDER_MAN_MAX_WEB_CHARGES, 0)
                        return
                    }
                    rechargeProgressTicks++
                    if (rechargeProgressTicks >= SPIDER_MAN_RECHARGE_TICKS) {
                        rechargeProgressTicks = 0
                        status.updateState(
                            status.power + 1,
                            if (status.power + 1 < SPIDER_MAN_MAX_WEB_CHARGES) SPIDER_MAN_RECHARGE_TICKS else 0,
                        )
                        particles.spawn(player, Particle.WHITE_ASH, count = 9, spread = 0.35, speed = 0.02)
                        sounds.playTo(player, Sound.BLOCK_COBWEB_PLACE, volume = 0.45f, pitch = 1.55f)
                    } else if (rechargeProgressTicks % 5 == 0) {
                        status.updateState(status.power, SPIDER_MAN_RECHARGE_TICKS - rechargeProgressTicks)
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
        }

        override fun isUseSuccess(): Boolean {
            initialize()
            if (webInFlight || swinging) {
                player.sendMiniMessage("<red><bold>[!] 이미 거미줄을 사용 중입니다. 웅크려서 놓을 수 있습니다.")
                return false
            }
            val status = playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }
            if (status.power <= 0) {
                player.sendMiniMessage("<red><bold>[!] 남은 거미줄 충전이 없습니다.")
                return false
            }
            return true
        }

        override fun use() {
            multiplyCurrentCooldown(0.0)
            val status = playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }
            status.updateState(status.power - 1, SPIDER_MAN_RECHARGE_TICKS - rechargeProgressTicks)
            status.setRopeState("발사 중")
            breakRequested = false
            webInFlight = true
            updateWebLine(ropeHandLocation(), player.eyeLocation, pullsToEntity = false)
            registerWebTarget()
            WebProjectile().apply {
                location = player.eyeLocation.clone()
                setContinueWhileIf { isProjectileActive() }
                spawnProjectile(playerData)
            }
            sounds.play(player, Sound.ENTITY_FISHING_BOBBER_THROW, volume = 1.0f, pitch = 1.45f)
        }

        private fun startSwing(anchorProvider: () -> Location?, pullsToEntity: Boolean) {
            webInFlight = false
            swinging = true
            val firstAnchor = anchorProvider()
            if (firstAnchor == null || firstAnchor.world != player.world) {
                clearActiveWeb()
                return
            }
            val originalGravity = player.hasGravity().also { swingOriginalGravity = it }
            val initialDelta = firstAnchor.toVector().subtract(ropeHandLocation().toVector())
            val actualInitialDistance = initialDelta.length()
            if (actualInitialDistance < 0.35) {
                swingOriginalGravity = null
                clearActiveWeb()
                return
            }
            val initialDistance = actualInitialDistance.coerceAtLeast(1.8)
            var ropeLength = if (pullsToEntity) initialDistance else (initialDistance - 0.45).coerceAtLeast(1.8)
            val maximumRopeLength = max(ropeLength + 3.0, 5.0)
            var releaseArmed = !player.isSneaking
            var ticks = 0
            var lastJumpInput = false
            var reelBurstCooldown = 0
            var tensionSoundCooldown = 0
            val launchedFromGround = !player.location.clone().subtract(0.0, 0.12, 0.0).block.isPassable
            val ropeStatus = playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }
            ropeStatus.setRopeState(if (pullsToEntity) "대상 추적" else "스윙 연결")
            val anchorDisplay = if (pullsToEntity) null else spawnAnchorDisplay(firstAnchor)
            activeAnchorDisplay = anchorDisplay
            updateWebLine(ropeHandLocation(), firstAnchor, pullsToEntity)

            player.setGravity(false)
            val initialInward = initialDelta.normalize()
            val launchVelocity = player.velocity.clone()
            val viewTangent = player.eyeLocation.direction.normalize().let { view ->
                view.subtract(initialInward.clone().multiply(view.dot(initialInward)))
            }
            launchVelocity.add(initialInward.clone().multiply(if (pullsToEntity) 0.62 else 0.38))
            if (viewTangent.lengthSquared() > 1.0E-6) {
                launchVelocity.add(viewTangent.normalize().multiply(0.28))
            }
            launchVelocity.y = max(
                launchVelocity.y,
                when {
                    launchedFromGround && pullsToEntity -> 0.52
                    launchedFromGround -> 0.68
                    pullsToEntity -> 0.18
                    else -> 0.3
                },
            )
            player.velocity = launchVelocity
            particles.spawn(player, Particle.CLOUD, count = if (launchedFromGround) 20 else 12, spread = 0.3, speed = 0.11)
            sounds.play(
                player,
                Sound.ENTITY_WIND_CHARGE_WIND_BURST,
                volume = if (launchedFromGround) 0.95f else 0.75f,
                pitch = if (launchedFromGround) 1.05f else 1.2f,
            )

            swingTask?.cancel()
            swingTask = playerData.trackTask(object : BukkitRunnable() {
                private fun finishSwing(voluntaryRelease: Boolean = false) {
                    if (voluntaryRelease) {
                        val releaseVelocity = player.velocity.clone()
                        val releaseSpeed = releaseVelocity.length()
                        if (releaseSpeed > 0.2) {
                            releaseVelocity.multiply(1.14)
                            releaseVelocity.y += if (releaseVelocity.y >= 0.0) 0.1 else 0.055
                            val maximumReleaseSpeed = 3.15
                            if (releaseVelocity.lengthSquared() > maximumReleaseSpeed * maximumReleaseSpeed) {
                                releaseVelocity.normalize().multiply(maximumReleaseSpeed)
                            }
                            player.velocity = releaseVelocity
                        }
                        particles.spawn(player, Particle.CLOUD, count = 15, spread = 0.3, speed = 0.1)
                        sounds.play(player, Sound.ENTITY_WIND_CHARGE_WIND_BURST, volume = 0.95f, pitch = 1.35f)
                    }
                    swinging = false
                    swingTask = null
                    anchorDisplay?.remove()
                    if (activeAnchorDisplay === anchorDisplay) activeAnchorDisplay = null
                    ropeStatus.setRopeState(null)
                    player.setGravity(originalGravity)
                    swingOriginalGravity = null
                    player.fallDistance = 0f
                    clearWebTarget()
                    particles.spawn(player, Particle.CLOUD, count = 8, spread = 0.24, speed = 0.05)
                    sounds.play(player, Sound.ENTITY_WIND_CHARGE_WIND_BURST, volume = 0.7f, pitch = 1.55f)
                    cancel()
                }

                override fun run() {
                    if (!player.isOnline || player.isDead) {
                        finishSwing()
                        return
                    }
                    if (isWebInterrupted()) {
                        finishSwing()
                        return
                    }
                    if (!player.isSneaking) releaseArmed = true
                    if (releaseArmed && player.isSneaking) {
                        finishSwing(voluntaryRelease = true)
                        return
                    }
                    val anchor = anchorProvider()
                    if (anchor == null || anchor.world != player.world) {
                        finishSwing()
                        return
                    }
                    val hand = ropeHandLocation()
                    val delta = anchor.toVector().subtract(hand.toVector())
                    val distance = delta.length()
                    if (distance < if (pullsToEntity) 1.15 else 0.55) {
                        finishSwing()
                        return
                    }
                    val inward = delta.normalize()
                    if (reelBurstCooldown > 0) reelBurstCooldown--
                    if (tensionSoundCooldown > 0) tensionSoundCooldown--
                    val jumpStarted = jumpInput && !lastJumpInput
                    lastJumpInput = jumpInput
                    var reelBurst = false

                    if (!pullsToEntity) {
                        val lettingOutRope = backwardInput && !jumpInput
                        if (jumpInput) ropeLength = (ropeLength - 0.19).coerceAtLeast(1.8)
                        if (jumpStarted && reelBurstCooldown <= 0) {
                            ropeLength = (ropeLength - 0.42).coerceAtLeast(1.8)
                            reelBurst = true
                            reelBurstCooldown = 8
                        }
                        if (lettingOutRope) {
                            ropeLength = (ropeLength + 0.045).coerceAtMost(maximumRopeLength)
                        } else {
                            val slack = (ropeLength - distance).coerceAtLeast(0.0)
                            if (slack > 0.025) {
                                val automaticReel = min(0.22, 0.035 + slack * 0.68)
                                ropeLength = (ropeLength - automaticReel).coerceAtLeast(1.8)
                            }
                        }
                        if (ticks < 14) {
                            val initialReelSpeed = if (launchedFromGround) 0.16 else 0.065
                            ropeLength = (ropeLength - initialReelSpeed).coerceAtLeast(1.8)
                        }
                    }

                    val velocity = player.velocity.clone().multiply(if (pullsToEntity) 0.988 else 0.995)
                    val diving = !pullsToEntity && forwardInput && velocity.y < -0.08
                    velocity.add(Vector(0.0, if (diving) -0.088 else -0.073, 0.0))
                    if (launchedFromGround && ticks < 10) {
                        velocity.y += 0.062
                        velocity.add(inward.clone().multiply(0.06))
                    }
                    applyDirectionalSwingInput(velocity, inward)

                    if (!pullsToEntity) {
                        val radialSpeed = velocity.dot(inward)
                        val tangentialVelocity = velocity.clone()
                            .subtract(inward.clone().multiply(radialSpeed))
                        val tangentialSpeed = tangentialVelocity.length()
                        val lowPointFactor = ((inward.y - 0.35) / 0.65).coerceIn(0.0, 1.0)
                        if (forwardInput && tangentialSpeed > 0.12) {
                            val descentFactor = (-velocity.y * 0.55).coerceIn(0.0, 0.3)
                            val swingPump = 0.018 + lowPointFactor * 0.038 + descentFactor
                            velocity.add(tangentialVelocity.normalize().multiply(swingPump))
                        }
                        if (reelBurst) {
                            velocity.add(inward.clone().multiply(0.28))
                            if (tangentialSpeed > 0.12) {
                                velocity.add(tangentialVelocity.normalize().multiply(0.13))
                            }
                            particles.spawn(player, Particle.CLOUD, count = 12, spread = 0.22, speed = 0.08)
                            sounds.play(player, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, volume = 0.85f, pitch = 1.45f)
                        }
                    }

                    if (pullsToEntity) {
                        velocity.add(inward.clone().multiply(0.29 + min(distance * 0.014, 0.16)))
                    } else {
                        if (distance >= ropeLength - 0.16) {
                            val outward = inward.clone().multiply(-1.0)
                            val outwardSpeed = velocity.dot(outward)
                            if (outwardSpeed > 0.0) {
                                velocity.subtract(outward.multiply(outwardSpeed))
                            }
                            val radialSpeed = velocity.dot(inward)
                            val tangentialVelocity = velocity.clone()
                                .subtract(inward.clone().multiply(radialSpeed))
                            val centripetalForce = (tangentialVelocity.lengthSquared() / ropeLength.coerceAtLeast(1.0))
                                .coerceIn(0.0, 0.48)
                            val stretch = (distance - ropeLength).coerceAtLeast(0.0)
                            val tensionForce = 0.14 + centripetalForce + stretch * 0.82
                            velocity.add(inward.clone().multiply(tensionForce))
                            if (tensionForce > 0.32 && tensionSoundCooldown <= 0) {
                                sounds.play(
                                    player,
                                    Sound.ENTITY_FISHING_BOBBER_RETRIEVE,
                                    volume = 0.3f,
                                    pitch = (1.15 + min(tensionForce, 0.7)).toFloat(),
                                )
                                tensionSoundCooldown = 7
                            }
                        } else {
                            velocity.add(inward.clone().multiply(0.055))
                        }

                        val predictedHand = hand.toVector().add(velocity)
                        val predictedToAnchor = anchor.toVector().subtract(predictedHand)
                        val predictedDistance = predictedToAnchor.length()
                        if (predictedDistance > ropeLength && predictedDistance > 1.0E-8) {
                            velocity.add(
                                predictedToAnchor.normalize().multiply(predictedDistance - ropeLength)
                            )
                        }
                    }

                    val maximumSpeed = if (pullsToEntity) 2.85 else 2.9
                    if (velocity.lengthSquared() > maximumSpeed * maximumSpeed) {
                        velocity.normalize().multiply(maximumSpeed)
                    }
                    player.velocity = velocity
                    player.fallDistance = 0f
                    updateWebLine(hand, anchor, pullsToEntity)
                    drawWeb(hand, anchor, ropeLength)

                    val currentSpeed = velocity.length()
                    if (currentSpeed > 0.95 && ticks % 2 == 0) {
                        particles.spawn(
                            player.location.clone().add(0.0, 0.85, 0.0)
                                .subtract(velocity.clone().normalize().multiply(0.45)),
                            Particle.CLOUD,
                            count = if (currentSpeed > 2.0) 4 else 2,
                            spread = 0.1,
                            speed = 0.025,
                        )
                    }
                    if (currentSpeed > 2.05 && ticks % 5 == 0) {
                        particles.spawn(
                            player.location.clone().add(0.0, 0.85, 0.0),
                            Particle.SWEEP_ATTACK,
                            count = 1,
                            spread = 0.02,
                        )
                    }
                    if (ticks % 12 == 0) {
                        sounds.play(
                            player,
                            Sound.ENTITY_WIND_CHARGE_WIND_BURST,
                            volume = (0.12 + currentSpeed * 0.06).toFloat().coerceAtMost(0.28f),
                            pitch = (1.35 + currentSpeed * 0.18).toFloat().coerceAtMost(1.9f),
                        )
                    }
                    if (ticks++ % 18 == 0) {
                        sounds.play(player, Sound.BLOCK_COBWEB_STEP, volume = 0.28f, pitch = 1.65f)
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }

        private fun applyDirectionalSwingInput(velocity: Vector, inward: Vector) {
            val forward = player.eyeLocation.direction.setY(0.0)
            if (forward.lengthSquared() < 1.0E-8) return
            forward.normalize()
            val right = Vector(-forward.z, 0.0, forward.x)
            val desired = Vector()
            if (forwardInput) desired.add(forward)
            if (backwardInput) desired.subtract(forward)
            if (rightInput) desired.add(right)
            if (leftInput) desired.subtract(right)
            if (desired.lengthSquared() < 1.0E-8) return

            desired.normalize()
            val tangent = desired.subtract(inward.clone().multiply(desired.dot(inward)))
            if (tangent.lengthSquared() < 1.0E-8) return
            val acceleration = when {
                jumpInput -> 0.115
                velocity.lengthSquared() > 2.56 -> 0.08
                else -> 0.088
            }
            velocity.add(tangent.normalize().multiply(acceleration))
        }

        private fun ropeHandLocation(): Location {
            val eye = player.eyeLocation.clone()
            val forward = eye.direction.normalize()
            var right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0))
            if (right.lengthSquared() < 1.0E-8) right = Vector(1.0, 0.0, 0.0)
            return eye.subtract(0.0, 0.28, 0.0).add(right.normalize().multiply(0.18))
        }

        private fun spawnAnchorDisplay(anchor: Location): BlockDisplay =
            anchor.world.spawn(anchor.clone().subtract(0.16, 0.16, 0.16), BlockDisplay::class.java).apply {
                block = Material.COBWEB.createBlockData()
                isPersistent = false
                transformation = Transformation(
                    Vector3f(),
                    Quaternionf(),
                    Vector3f(0.32f, 0.32f, 0.32f),
                    Quaternionf(),
                )
                TemporaryDisplayManager.mark(this, player.uniqueId)
            }

        private fun registerWebTarget() {
            webTargetRegistration?.unregister()
            webTargetRegistration = AttackableObjectManager.register(
                ownerId = player.uniqueId,
                world = player.world,
                acceptsAreaSkills = true,
                canBeHitBy = ::canWebBeHitBy,
                hitboxes = ::currentWebHitboxes,
                onHit = ::requestWebBreak,
            )
        }

        private fun canWebBeHitBy(attackerId: java.util.UUID?): Boolean {
            if (attackerId == null) return true
            if (attackerId == player.uniqueId) return false
            val attackerData = game.playerDatas.filterIsInstance<PlayerData>()
                .firstOrNull { it.uniqueId == attackerId }
            return attackerData?.isEnemyOf(playerData) ?: true
        }

        private fun updateWebLine(start: Location, end: Location, pullsToEntity: Boolean) {
            webStart = start.clone()
            webEnd = end.clone()
            webPullsToEntity = pullsToEntity
        }

        private fun currentWebHitboxes(): List<BoundingBox> {
            if (!webInFlight && !swinging) return emptyList()
            val start = webStart ?: return emptyList()
            val end = webEnd ?: return emptyList()
            if (start.world != end.world) return emptyList()
            val difference = end.toVector().subtract(start.toVector())
            val distance = difference.length()
            if (distance < 0.2) return emptyList()
            val steps = ceil(distance / 0.36).toInt().coerceAtLeast(1)
            val firstStep = ceil(steps * 0.16).toInt().coerceAtMost(steps)
            val lastStep = if (webPullsToEntity) {
                (steps * 0.84).toInt().coerceAtLeast(firstStep)
            } else {
                steps
            }
            val radius = 0.14
            return (firstStep..lastStep).map { index ->
                val point = start.toVector().add(difference.clone().multiply(index.toDouble() / steps))
                BoundingBox(
                    point.x - radius,
                    point.y - radius,
                    point.z - radius,
                    point.x + radius,
                    point.y + radius,
                    point.z + radius,
                )
            }
        }

        private fun requestWebBreak() {
            if ((!webInFlight && !swinging) || breakRequested) return
            breakRequested = true
            val effectLocation = webStart?.clone()?.apply {
                val end = webEnd
                if (end != null && end.world == world) {
                    add(end.toVector().subtract(toVector()).multiply(0.5))
                }
            } ?: player.location
            particles.spawn(effectLocation, Particle.CLOUD, count = 9, spread = 0.22, speed = 0.04)
            sounds.play(effectLocation, Sound.BLOCK_COBWEB_BREAK, volume = 0.75f, pitch = 1.35f)
        }

        private fun isWebInterrupted(): Boolean {
            if (!breakRequested && (playerData.hasStatus<Silence>() || playerData.hasStatus<Snare>())) {
                requestWebBreak()
            }
            return breakRequested
        }

        private fun clearWebTarget() {
            webTargetRegistration?.unregister()
            webTargetRegistration = null
            webStart = null
            webEnd = null
            webPullsToEntity = false
        }

        private fun clearActiveWeb() {
            webInFlight = false
            swinging = false
            clearWebTarget()
            playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }.setRopeState(null)
        }

        private fun drawWeb(from: Location, to: Location, ropeLength: Double) {
            val difference = to.toVector().subtract(from.toVector())
            val distance = difference.length()
            if (distance < 1.0E-8) return
            val points = ceil(distance / 0.27).toInt().coerceAtLeast(1)
            val sag = (0.05 + (ropeLength - distance).coerceAtLeast(0.0) * 0.42).coerceAtMost(1.5)
            repeat(points + 1) { index ->
                val progress = index.toDouble() / points
                val current = from.clone().add(difference.clone().multiply(progress))
                current.y -= 4.0 * progress * (1.0 - progress) * sag
                particles.spawn(
                    current,
                    Particle.DUST,
                    Particle.DustOptions(Color.WHITE, 0.66f),
                    ParticleOptions(count = 1),
                )
                if (index % 7 == 0) {
                    particles.spawn(current, Particle.WHITE_ASH, count = 1)
                }
            }
        }

        private inner class WebProjectile : Projectile() {
            override lateinit var location: Location
            override var targetType = TargetType.Enemy
            override var speed = 3.0
            override var isWallHit = false
            override var isPlayerHit = true
            override val isPlayerHitRemove = true
            override var time: Int? = 3
            override var xSize = 1.25
            override var ySize = 1.25
            override var zSize = 1.25

            private lateinit var flightDirection: Vector
            private var attached = false
            private var projectileActive = true
            private var moveTicks = 0
            private var previousMoveLocation: Location? = null

            fun isProjectileActive(): Boolean = projectileActive && !isWebInterrupted()

            override fun onProjectileMove(location: Location) {
                updateWebLine(ropeHandLocation(), location, pullsToEntity = false)
                if (!::flightDirection.isInitialized) flightDirection = location.direction.normalize()
                val previous = previousMoveLocation ?: this.location.clone()
                val movement = location.toVector().subtract(previous.toVector())
                val distance = movement.length()
                if (!attached && distance > 1.0E-8) {
                    val blockHit = location.world.rayTraceBlocks(
                        previous,
                        movement.normalize(),
                        distance,
                        FluidCollisionMode.NEVER,
                        true,
                    )
                    val hitPosition = blockHit?.hitPosition
                    val hitFace = blockHit?.hitBlockFace
                    if (hitPosition != null && hitFace != null) {
                        attached = true
                        projectileActive = false
                        val surface = hitPosition.toLocation(location.world)
                            .add(hitFace.direction.multiply(0.04))
                        particles.spawn(surface, Particle.CLOUD, count = 10, spread = 0.2, speed = 0.02)
                        sounds.play(surface, Sound.BLOCK_COBWEB_PLACE, volume = 0.9f, pitch = 1.25f)
                        startSwing({ surface.clone() }, pullsToEntity = false)
                        return
                    }
                }
                previousMoveLocation = location.clone()
                particles.spawn(
                    location,
                    Particle.DUST,
                    Particle.DustOptions(Color.WHITE, 0.85f),
                    ParticleOptions(count = 2, offsetX = 0.05, offsetY = 0.05, offsetZ = 0.05),
                )
                if (++moveTicks >= 60) projectileActive = false
            }

            override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
                attached = true
                projectileActive = false
                val status = playerData.getOrCreateStatus(playerData) { SpiderWebChargeStatus() }
                rechargeProgressTicks = 0
                status.updateState(0, 120)
                particles.spawn(hitEntityData.entity, Particle.CLOUD, count = 13, spread = 0.35, speed = 0.02)
                sounds.play(hitEntityData.entity, Sound.ENTITY_SPIDER_AMBIENT, volume = 0.65f, pitch = 1.65f)
                startSwing(
                    anchorProvider = {
                        if (!hitEntityData.entity.isValid || hitEntityData.entityStatus.isDead) null
                        else hitEntityData.entity.location.clone().add(0.0, hitEntityData.entity.height * 0.6, 0.0)
                    },
                    pullsToEntity = true,
                )
            }

            override fun onProjectileEnd(location: Location) {
                if (!attached) {
                    clearActiveWeb()
                }
            }
        }
    }
}
