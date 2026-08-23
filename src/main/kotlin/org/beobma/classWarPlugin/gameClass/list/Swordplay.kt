package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.DisplayOrientationUtil
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val SWORDPLAY_BLOSSOM_COOLDOWN_SECONDS = 20
private const val SWORDPLAY_INFINITE_COOLDOWN_SECONDS = 60
private const val SWORDPLAY_SWORD_DAMAGE = 1.0
private const val SWORDPLAY_BLOSSOM_DAMAGE = 4.0

class Swordplay : GameClass(), GameStatusHandler {
    override val name = "<gray>이기어검"
    override val rank = Rank.S
    override val classItemMaterial = Material.GOLDEN_SWORD

    private val blossomSkill = BlossomSkill()
    private val infiniteSkill = InfiniteSkill()
    override var skills: List<Skill> = listOf(blossomSkill, infiniteSkill)
    override var passives: List<BasePassive> = listOf(SwordsmanshipPassive())

    private enum class SwordFlightMode {
        OWNER_ORBIT,
        APPROACHING,
        TARGET_ORBIT,
        RETURNING,
    }

    private data class FlyingSword(
        val display: ItemDisplay,
        var position: Location,
        var target: EntityData? = null,
        var attackCooldownTicks: Int = 0,
        var mode: SwordFlightMode = SwordFlightMode.OWNER_ORBIT,
        var movementSpeed: Double = 0.0,
        var targetOrbitAngle: Double = 0.0,
        var targetOrbitPathSpeed: Double = 0.0,
        var targetOrbitHitArmed: Boolean = false,
        var pierceForward: Vector = Vector(1.0, 0.0, 0.0),
        var pierceLoopAxis: Vector = Vector(0.0, 1.0, 0.0),
    )

    private val baseSwords = mutableListOf<FlyingSword>()
    private var passiveTask: BukkitTask? = null
    private var passiveTick = 0
    private var blossomActive = false
    private var infiniteTask: BukkitTask? = null
    private val infiniteSwords = mutableListOf<FlyingSword>()
    private val infiniteHitCounts = mutableMapOf<UUID, Int>()

    override fun onBattleStart() {
        resetSwordState()
        ensurePassiveSwords()
    }

    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        ensurePassiveSwords()
    }

    private fun ensurePassiveSwords() {
        baseSwords.removeAll { sword ->
            if (sword.display.isValid) return@removeAll false
            true
        }
        var created = 0
        while (baseSwords.size < PASSIVE_SWORD_COUNT) {
            val index = baseSwords.size
            val spawn = passiveOrbitLocation(index, passiveTick)
            baseSwords += FlyingSword(
                display = spawnSwordDisplay(spawn, Material.IRON_SWORD, BASE_SWORD_SCALE),
                position = spawn,
                attackCooldownTicks = index * 6,
            )
            created++
        }
        if (created > 0) {
            sounds.play(player, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 0.7f, pitch = 1.7f)
            sounds.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 0.45f, pitch = 1.8f)
        }
        if (passiveTask == null || passiveTask?.isCancelled == true) startPassiveTask()
    }

    private fun startPassiveTask() {
        passiveTask = playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    baseSwords.forEach { it.display.remove() }
                    baseSwords.clear()
                    passiveTask = null
                    cancel()
                    return
                }

                if (!blossomActive) {
                    val enemies = nearbyEnemies(PASSIVE_TARGET_RADIUS)
                    baseSwords.toList().forEachIndexed { index, sword ->
                        if (!sword.display.isValid) return@forEachIndexed
                        tickBaseSword(sword, index, enemies)
                    }
                }
                passiveTick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun tickBaseSword(sword: FlyingSword, index: Int, enemies: List<EntityData>) {
        if (sword.attackCooldownTicks > 0) sword.attackCooldownTicks--
        if (sword.target != null && !isValidTarget(sword.target, PASSIVE_TARGET_LEASH)) {
            releaseSwordFromTarget(sword, BASE_REACQUIRE_DELAY_TICKS)
        }
        if (
            sword.mode == SwordFlightMode.OWNER_ORBIT &&
            sword.target == null &&
            sword.attackCooldownTicks <= 0
        ) {
            val target = enemies.minByOrNull { candidate ->
                HitboxUtil.distanceSquared(candidate.entity.boundingBox, sword.position.toVector())
            }
            if (target != null) beginApproach(sword, target, BASE_APPROACH_START_SPEED)
        }

        val target = sword.target
        if (target == null) {
            tickOwnerOrbit(
                sword = sword,
                destination = passiveOrbitLocation(index, passiveTick),
                tangent = passiveOrbitTangent(index, passiveTick),
                scale = BASE_SWORD_SCALE,
                returnAcceleration = BASE_RETURN_ACCELERATION,
                returnMaxSpeed = BASE_RETURN_MAX_SPEED,
            )
            if (passiveTick % 8 == index * 2) {
                particles.spawn(sword.position, Particle.ENCHANT, count = 2, spread = 0.08, speed = 0.01)
            }
            return
        }

        if (sword.mode == SwordFlightMode.TARGET_ORBIT) {
            tickBaseTargetOrbit(sword, index, target)
            return
        }

        val targetCenter = target.entity.boundingBox.center.toLocation(target.entity.world)
        val previous = sword.position.clone()
        sword.movementSpeed = min(
            BASE_APPROACH_MAX_SPEED,
            sword.movementSpeed + BASE_APPROACH_ACCELERATION,
        )
        moveSwordToward(sword, targetCenter, sword.movementSpeed)
        val direction = sword.position.toVector().subtract(previous.toVector())
        if (direction.lengthSquared() > 1.0E-8) {
            DisplayOrientationUtil.alignSwordBladeVertically(sword.display, direction, BASE_SWORD_SCALE)
        }
        if (passiveTick % 2 == 0) {
            particles.line(previous, sword.position, Particle.END_ROD, spacing = 0.42)
        }

        if (!HitboxUtil.intersectsSegment(
                target.entity.boundingBox,
                previous.toVector(),
                sword.position.toVector(),
                expansion = BASE_SWORD_HITBOX_EXPANSION,
            )
        ) return

        target.damage(SWORDPLAY_SWORD_DAMAGE, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        playSwordHit(target, strong = false)
        beginTargetOrbit(
            sword = sword,
            index = index,
            targetCenter = targetCenter,
            entryDirection = direction,
            orbitRadius = BASE_TARGET_ORBIT_RADIUS,
            initialPathSpeed = BASE_TARGET_ORBIT_START_PATH_SPEED,
        )
    }

    private fun tickBaseTargetOrbit(sword: FlyingSword, index: Int, target: EntityData) {
        val targetCenter = target.entity.boundingBox.center.toLocation(target.entity.world)
        val previous = sword.position.clone()
        sword.targetOrbitPathSpeed = min(
            BASE_TARGET_ORBIT_MAX_PATH_SPEED,
            sword.targetOrbitPathSpeed + BASE_TARGET_ORBIT_PATH_ACCELERATION,
        )
        val previousAngle = sword.targetOrbitAngle
        val angleStep = targetOrbitAngleStep(
            sword = sword,
            angle = previousAngle,
            radius = BASE_TARGET_ORBIT_RADIUS,
            pathSpeed = sword.targetOrbitPathSpeed,
        )
        sword.targetOrbitAngle = normalizeOrbitAngle(
            previousAngle + angleStep,
        )
        val destination = targetOrbitLocation(
            center = targetCenter,
            sword = sword,
            angle = sword.targetOrbitAngle,
            radius = BASE_TARGET_ORBIT_RADIUS,
        )
        teleportSwordPrecisely(sword, destination)

        val tangent = targetOrbitTangent(
            sword,
            sword.targetOrbitAngle,
            BASE_TARGET_ORBIT_RADIUS,
        )
        if (tangent.lengthSquared() > 1.0E-8) {
            DisplayOrientationUtil.alignSwordBladeVertically(sword.display, tangent, BASE_SWORD_SCALE)
        }
        if ((passiveTick + index) % 3 == 0) {
            particles.line(previous, sword.position, Particle.END_ROD, spacing = 0.38)
        }

        val intersectsTarget = HitboxUtil.intersectsSegment(
            target.entity.boundingBox,
            previous.toVector(),
            sword.position.toVector(),
            expansion = BASE_SWORD_HITBOX_EXPANSION,
        )
        if (!sword.targetOrbitHitArmed) {
            if (
                !intersectsTarget &&
                HitboxUtil.distanceSquared(target.entity.boundingBox, sword.position.toVector()) >
                TARGET_PIERCE_REARM_DISTANCE_SQUARED
            ) sword.targetOrbitHitArmed = true
            return
        }
        if (!intersectsTarget) return

        sword.targetOrbitHitArmed = false
        target.damage(SWORDPLAY_SWORD_DAMAGE, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        playSwordHit(target, strong = false)
    }

    private inner class BlossomSkill : Skill() {
        override val name = "<bold>블로섬"
        override val description = listOf(
            "<gray>어검술로 소환된 검을 해당 위치에서 회전시켜 적중한 모든 적에게 4의 피해를 입힌다.",
            "<gray>여러 검에 피격되더라도 피해는 한 번만 입는다.",
        )
        override val cooldown = SWORDPLAY_BLOSSOM_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (blossomActive) {
                player.sendMiniMessage("<red><bold>[!] 이미 블로섬이 진행 중입니다.")
                return false
            }
            ensurePassiveSwords()
            if (baseSwords.size >= PASSIVE_SWORD_COUNT) return true
            player.sendMiniMessage("<red><bold>[!] 회전시킬 어검이 부족합니다.")
            return false
        }

        override fun use() {
            blossomActive = true
            val centers = baseSwords.map { it.position.clone() }
            val hitTargets = mutableSetOf<UUID>()
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 1.1f, pitch = 0.62f)
            sounds.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 0.8f, pitch = 1.45f)

            playerData.trackTask(object : BukkitRunnable() {
                private var tick = 0

                override fun run() {
                    if (!player.isOnline || playerStatus.isDead || tick > BLOSSOM_DURATION_TICKS) {
                        blossomActive = false
                        cancel()
                        return
                    }

                    baseSwords.toList().forEachIndexed { index, sword ->
                        val center = centers.getOrNull(index) ?: return@forEachIndexed
                        if (!sword.display.isValid) return@forEachIndexed
                        val angle = tick * BLOSSOM_ROTATION_SPEED + index * (2.0 * PI / PASSIVE_SWORD_COUNT)
                        val direction = Vector(cos(angle), 0.0, sin(angle))
                        sword.position = center.clone()
                        sword.display.teleport(center)
                        DisplayOrientationUtil.alignSwordBladeHorizontally(
                            sword.display,
                            direction,
                            BLOSSOM_SWORD_SCALE,
                        )

                        val bladeStart = center.clone().subtract(direction.clone().multiply(BLOSSOM_BLADE_HALF_LENGTH))
                        val bladeEnd = center.clone().add(direction.clone().multiply(BLOSSOM_BLADE_HALF_LENGTH))
                        playerData.radius(center, TargetType.Enemy, BLOSSOM_HIT_RADIUS, false)
                            .forEach { target ->
                                if (target.entity.uniqueId in hitTargets) return@forEach
                                if (!HitboxUtil.intersectsSegment(
                                        target.entity.boundingBox,
                                        bladeStart.toVector(),
                                        bladeEnd.toVector(),
                                        expansion = BLOSSOM_HITBOX_EXPANSION,
                                    )
                                ) return@forEach

                                hitTargets += target.entity.uniqueId
                                target.damage(SWORDPLAY_BLOSSOM_DAMAGE, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                                playSwordHit(target, strong = true)
                            }
                        if (tick % 2 == 0) {
                            particles.line(bladeStart, bladeEnd, Particle.SWEEP_ATTACK, spacing = 0.8)
                        }
                    }

                    if (tick % 4 == 0) {
                        centers.forEach { center ->
                            particles.spawn(center, Particle.END_ROD, count = 4, spread = 0.28, speed = 0.025)
                        }
                        sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 0.55f, pitch = 1.25f)
                    }
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }
    }

    private inner class InfiniteSkill : Skill() {
        override val name = "<bold>인피니트"
        override val description = listOf(
            "<gray>20초간 무수한 검이 창조되는 공간을 만든다.",
            "<gray>18자루의 다이아몬드 검이 구형 궤도로 자신 주위를 공전하며 주위의 적을 공격한다.",
            "<gray>타격한 검은 대상을 꿰뚫는 ∞ 궤도로 가속하며 계속 공격한다.",
            "<gray>적은 인피니트로 소환된 검에 5번 피격될 때마다 1의 피해를 입는다.",
            "<gray>인피니트로 소환된 검은 블로섬 스킬의 영향을 받지 않는다.",
        )
        override val cooldown = SWORDPLAY_INFINITE_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (infiniteTask == null || infiniteTask?.isCancelled == true) return true
            player.sendMiniMessage("<red><bold>[!] 이미 인피니트가 전개되어 있습니다.")
            return false
        }

        override fun use() = startInfinite()
    }

    private fun startInfinite() {
        clearInfinite(playEndEffect = false)
        infiniteHitCounts.clear()
        repeat(INFINITE_SWORD_COUNT) { index ->
            val spawn = infiniteOrbitLocation(index, 0)
            infiniteSwords += FlyingSword(
                display = spawnSwordDisplay(spawn, Material.DIAMOND_SWORD, INFINITE_SWORD_SCALE),
                position = spawn,
                attackCooldownTicks = index % 12,
            )
        }
        particles.spawn(player, Particle.REVERSE_PORTAL, count = 120, spread = 2.2, speed = 0.18)
        particles.spawn(player, Particle.ENCHANT, count = 100, spread = 2.8, speed = 0.12)
        sounds.play(player, Sound.BLOCK_END_PORTAL_SPAWN, volume = 0.72f, pitch = 1.35f)
        sounds.play(player, Sound.ITEM_TRIDENT_THUNDER, volume = 0.55f, pitch = 1.65f)

        infiniteTask = playerData.trackTask(object : BukkitRunnable() {
            private var tick = 0

            override fun run() {
                if (!player.isOnline || playerStatus.isDead || tick >= INFINITE_DURATION_TICKS) {
                    clearInfinite(playEndEffect = player.isOnline && !playerStatus.isDead)
                    cancel()
                    return
                }

                val enemies = nearbyEnemies(INFINITE_TARGET_RADIUS)
                infiniteSwords.toList().forEachIndexed { index, sword ->
                    if (!sword.display.isValid) return@forEachIndexed
                    tickInfiniteSword(sword, index, tick, enemies)
                }
                if (tick % 10 == 0) {
                    drawInfiniteSphere(tick)
                    sounds.play(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.28f, pitch = 1.5f)
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun tickInfiniteSword(
        sword: FlyingSword,
        index: Int,
        tick: Int,
        enemies: List<EntityData>,
    ) {
        if (sword.attackCooldownTicks > 0) sword.attackCooldownTicks--
        if (sword.target != null && !isValidTarget(sword.target, INFINITE_TARGET_LEASH)) {
            releaseSwordFromTarget(sword, INFINITE_REACQUIRE_DELAY_TICKS + index % 4)
        }
        if (
            sword.mode == SwordFlightMode.OWNER_ORBIT &&
            sword.target == null &&
            sword.attackCooldownTicks <= 0 &&
            enemies.isNotEmpty()
        ) {
            beginApproach(
                sword,
                enemies[(index + tick / 5) % enemies.size],
                INFINITE_APPROACH_START_SPEED,
            )
        }

        val target = sword.target
        if (target == null) {
            tickOwnerOrbit(
                sword = sword,
                destination = infiniteOrbitLocation(index, tick),
                tangent = infiniteOrbitTangent(index, tick),
                scale = INFINITE_SWORD_SCALE,
                returnAcceleration = INFINITE_RETURN_ACCELERATION,
                returnMaxSpeed = INFINITE_RETURN_MAX_SPEED,
            )
            return
        }

        if (sword.mode == SwordFlightMode.TARGET_ORBIT) {
            tickInfiniteTargetOrbit(sword, index, tick, target)
            return
        }

        val targetCenter = target.entity.boundingBox.center.toLocation(target.entity.world)
        val previous = sword.position.clone()
        sword.movementSpeed = min(
            INFINITE_APPROACH_MAX_SPEED,
            sword.movementSpeed + INFINITE_APPROACH_ACCELERATION,
        )
        moveSwordToward(sword, targetCenter, sword.movementSpeed)
        val direction = sword.position.toVector().subtract(previous.toVector())
        if (direction.lengthSquared() > 1.0E-8) {
            DisplayOrientationUtil.alignSwordBladeVertically(
                sword.display,
                direction,
                INFINITE_SWORD_SCALE,
            )
        }
        if ((tick + index) % 3 == 0) particles.line(previous, sword.position, Particle.ENCHANT, spacing = 0.55)

        if (!HitboxUtil.intersectsSegment(
                target.entity.boundingBox,
                previous.toVector(),
                sword.position.toVector(),
                expansion = INFINITE_HITBOX_EXPANSION,
            )
        ) return

        registerInfiniteHit(target)
        val orbitRadius = INFINITE_TARGET_ORBIT_RADIUS + (index % 3) * INFINITE_TARGET_ORBIT_RADIUS_STEP
        beginTargetOrbit(
            sword = sword,
            index = index,
            targetCenter = targetCenter,
            entryDirection = direction,
            orbitRadius = orbitRadius,
            initialPathSpeed = INFINITE_TARGET_ORBIT_START_PATH_SPEED,
        )
    }

    private fun tickInfiniteTargetOrbit(
        sword: FlyingSword,
        index: Int,
        tick: Int,
        target: EntityData,
    ) {
        val targetCenter = target.entity.boundingBox.center.toLocation(target.entity.world)
        val previous = sword.position.clone()
        val radius = INFINITE_TARGET_ORBIT_RADIUS + (index % 3) * INFINITE_TARGET_ORBIT_RADIUS_STEP
        sword.targetOrbitPathSpeed = min(
            INFINITE_TARGET_ORBIT_MAX_PATH_SPEED,
            sword.targetOrbitPathSpeed + INFINITE_TARGET_ORBIT_PATH_ACCELERATION,
        )
        val previousAngle = sword.targetOrbitAngle
        val angleStep = targetOrbitAngleStep(
            sword = sword,
            angle = previousAngle,
            radius = radius,
            pathSpeed = sword.targetOrbitPathSpeed,
        )
        sword.targetOrbitAngle = normalizeOrbitAngle(
            previousAngle + angleStep,
        )
        val destination = targetOrbitLocation(
            center = targetCenter,
            sword = sword,
            angle = sword.targetOrbitAngle,
            radius = radius,
        )
        teleportSwordPrecisely(sword, destination)

        val tangent = targetOrbitTangent(
            sword,
            sword.targetOrbitAngle,
            radius,
        )
        if (tangent.lengthSquared() > 1.0E-8) {
            DisplayOrientationUtil.alignSwordBladeVertically(sword.display, tangent, INFINITE_SWORD_SCALE)
        }
        if ((tick + index) % 4 == 0) {
            particles.line(previous, sword.position, Particle.ENCHANT, spacing = 0.48)
        }

        val intersectsTarget = HitboxUtil.intersectsSegment(
            target.entity.boundingBox,
            previous.toVector(),
            sword.position.toVector(),
            expansion = INFINITE_HITBOX_EXPANSION,
        )
        if (!sword.targetOrbitHitArmed) {
            if (
                !intersectsTarget &&
                HitboxUtil.distanceSquared(target.entity.boundingBox, sword.position.toVector()) >
                TARGET_PIERCE_REARM_DISTANCE_SQUARED
            ) sword.targetOrbitHitArmed = true
            return
        }
        if (!intersectsTarget) return

        sword.targetOrbitHitArmed = false
        registerInfiniteHit(target)
    }

    private fun registerInfiniteHit(target: EntityData) {
        val targetId = target.entity.uniqueId
        val hits = (infiniteHitCounts[targetId] ?: 0) + 1
        particles.spawn(target.entity, Particle.ELECTRIC_SPARK, count = 5, spread = 0.28, speed = 0.05)
        sounds.play(target.entity, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, volume = 0.35f, pitch = 1.65f)
        if (hits < INFINITE_HITS_PER_DAMAGE) {
            infiniteHitCounts[targetId] = hits
            return
        }

        infiniteHitCounts[targetId] = 0
        target.damage(SWORDPLAY_SWORD_DAMAGE, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        particles.spawn(target.entity, Particle.CRIT, count = 18, spread = 0.42, speed = 0.12)
        particles.spawn(target.entity, Particle.SWEEP_ATTACK, count = 2, spread = 0.3, speed = 0.04)
        sounds.play(target.entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.78f, pitch = 1.4f)
    }

    private fun drawInfiniteSphere(tick: Int) {
        val center = player.location.clone().add(0.0, INFINITE_ORBIT_CENTER_HEIGHT, 0.0)
        repeat(INFINITE_ORBIT_PLANE_COUNT) { plane ->
            val radius = INFINITE_ORBIT_INNER_RADIUS + (plane % 3) * INFINITE_ORBIT_RADIUS_STEP
            val inclination = Math.toRadians(-70.0 + plane * 28.0)
            val yaw = plane * PI / INFINITE_ORBIT_PLANE_COUNT
            repeat(INFINITE_ORBIT_PARTICLE_POINTS) { point ->
                val angle = 2.0 * PI * point / INFINITE_ORBIT_PARTICLE_POINTS + tick * 0.024
                val location = center.clone().add(tiltedOrbitOffset(angle, radius, inclination, yaw))
                particles.spawn(
                    location,
                    if ((plane + point) % 4 == 0) Particle.END_ROD else Particle.ENCHANT,
                    count = 1,
                )
            }
        }
    }

    private fun nearbyEnemies(radius: Double): List<EntityData> =
        playerData.radius(player.location, TargetType.Enemy, radius, false)

    private fun isValidTarget(target: EntityData?, leash: Double): Boolean {
        if (target == null || !target.entity.isValid || target.entity.isDead) return false
        if (target.entityStatus.isDead || !target.entityStatus.isSkillTargeting) return false
        if (target.entity.world != player.world) return false
        return HitboxUtil.distanceSquared(target.entity.boundingBox, player.boundingBox) <= leash * leash
    }

    private fun passiveOrbitLocation(index: Int, tick: Int): Location {
        val angle = tick * PASSIVE_ORBIT_SPEED + index * (2.0 * PI / PASSIVE_SWORD_COUNT)
        val inclination = Math.toRadians(-58.0 + index * 58.0)
        val yaw = index * PI / PASSIVE_SWORD_COUNT
        return player.location.clone().add(0.0, PASSIVE_ORBIT_CENTER_HEIGHT, 0.0).add(
            tiltedOrbitOffset(angle, PASSIVE_ORBIT_RADIUS, inclination, yaw),
        )
    }

    private fun passiveOrbitTangent(index: Int, tick: Int): Vector =
        passiveOrbitLocation(index, tick + 1).toVector().subtract(passiveOrbitLocation(index, tick).toVector())

    private fun infiniteOrbitLocation(index: Int, tick: Int): Location {
        val shell = index % INFINITE_ORBIT_SHELL_COUNT
        val plane = index / INFINITE_ORBIT_SHELL_COUNT
        val speed = INFINITE_ORBIT_SPEED + shell * INFINITE_ORBIT_SHELL_SPEED_STEP
        val angle = tick * speed + plane * (2.0 * PI / INFINITE_ORBIT_PLANE_COUNT) + shell * 0.42
        val radius = INFINITE_ORBIT_INNER_RADIUS + shell * INFINITE_ORBIT_RADIUS_STEP
        val inclination = Math.toRadians(-70.0 + plane * 28.0)
        val yaw = plane * PI / INFINITE_ORBIT_PLANE_COUNT + shell * 0.21
        return player.location.clone().add(0.0, INFINITE_ORBIT_CENTER_HEIGHT, 0.0).add(
            tiltedOrbitOffset(angle, radius, inclination, yaw),
        )
    }

    private fun infiniteOrbitTangent(index: Int, tick: Int): Vector =
        infiniteOrbitLocation(index, tick + 1).toVector().subtract(infiniteOrbitLocation(index, tick).toVector())

    private fun targetOrbitLocation(
        center: Location,
        sword: FlyingSword,
        angle: Double,
        radius: Double,
    ): Location {
        val wave = sin(angle)
        val forwardOffset = sword.pierceForward.clone().multiply(wave * radius)
        val loopOffset = sword.pierceLoopAxis.clone().multiply(
            wave * sin(2.0 * angle) * radius * TARGET_PIERCE_LOOP_WIDTH_RATIO,
        )
        return center.clone().add(forwardOffset).add(loopOffset)
    }

    private fun targetOrbitTangent(sword: FlyingSword, angle: Double, radius: Double): Vector {
        val forwardDerivative = cos(angle) * radius
        val loopDerivative = (
            cos(angle) * sin(2.0 * angle) +
                2.0 * sin(angle) * cos(2.0 * angle)
            ) * radius * TARGET_PIERCE_LOOP_WIDTH_RATIO
        return sword.pierceForward.clone().multiply(forwardDerivative)
            .add(sword.pierceLoopAxis.clone().multiply(loopDerivative))
    }

    private fun targetOrbitAngleStep(
        sword: FlyingSword,
        angle: Double,
        radius: Double,
        pathSpeed: Double,
    ): Double {
        val currentDerivativeLength = targetOrbitTangent(sword, angle, radius).length()
            .coerceAtLeast(TARGET_PIERCE_MIN_DERIVATIVE_LENGTH)
        val roughStep = (pathSpeed / currentDerivativeLength)
            .coerceIn(TARGET_PIERCE_MIN_ANGLE_STEP, TARGET_PIERCE_MAX_ANGLE_STEP)
        val midpointDerivativeLength = targetOrbitTangent(sword, angle + roughStep * 0.5, radius).length()
            .coerceAtLeast(TARGET_PIERCE_MIN_DERIVATIVE_LENGTH)
        return (pathSpeed / midpointDerivativeLength)
            .coerceIn(TARGET_PIERCE_MIN_ANGLE_STEP, TARGET_PIERCE_MAX_ANGLE_STEP)
    }

    private fun normalizeOrbitAngle(angle: Double): Double {
        val fullTurn = 2.0 * PI
        val normalized = angle % fullTurn
        return if (normalized < 0.0) normalized + fullTurn else normalized
    }

    private fun tiltedOrbitOffset(
        angle: Double,
        radius: Double,
        inclination: Double,
        yaw: Double,
    ): Vector {
        val localX = cos(angle) * radius
        val localY = sin(angle) * radius * sin(inclination)
        val localZ = sin(angle) * radius * cos(inclination)
        return Vector(
            localX * cos(yaw) - localZ * sin(yaw),
            localY,
            localX * sin(yaw) + localZ * cos(yaw),
        )
    }

    private fun beginApproach(sword: FlyingSword, target: EntityData, startSpeed: Double) {
        sword.target = target
        sword.mode = SwordFlightMode.APPROACHING
        sword.movementSpeed = startSpeed
        sword.targetOrbitHitArmed = false
    }

    private fun beginTargetOrbit(
        sword: FlyingSword,
        index: Int,
        targetCenter: Location,
        entryDirection: Vector,
        orbitRadius: Double,
        initialPathSpeed: Double,
    ) {
        val forward = if (entryDirection.lengthSquared() > 1.0E-8) {
            entryDirection.clone().normalize()
        } else {
            player.location.direction.normalize()
        }
        val reference = if (abs(forward.y) < 0.92) Vector(0.0, 1.0, 0.0) else Vector(1.0, 0.0, 0.0)
        val loopAxis = forward.clone()
            .crossProduct(reference)
            .normalize()
            .rotateAroundAxis(
                forward,
                TARGET_PIERCE_BASE_ROLL + index * TARGET_PIERCE_ROLL_STEP,
            )
        val relativePosition = sword.position.toVector().subtract(targetCenter.toVector())
        val entryProjection = relativePosition.dot(forward)
        val entryAngle = asin(
            (entryProjection / orbitRadius).coerceIn(
                -TARGET_PIERCE_MAX_ENTRY_PROJECTION,
                TARGET_PIERCE_MAX_ENTRY_PROJECTION,
            ),
        )
        sword.mode = SwordFlightMode.TARGET_ORBIT
        sword.movementSpeed = initialPathSpeed
        sword.targetOrbitAngle = entryAngle
        sword.targetOrbitPathSpeed = initialPathSpeed
        sword.targetOrbitHitArmed = false
        sword.pierceForward = forward
        sword.pierceLoopAxis = loopAxis
    }

    private fun releaseSwordFromTarget(sword: FlyingSword, reacquireDelayTicks: Int) {
        sword.target = null
        sword.mode = SwordFlightMode.RETURNING
        sword.movementSpeed = (sword.movementSpeed * RETURN_RETAINED_SPEED_RATIO)
            .coerceAtLeast(RETURN_MIN_START_SPEED)
        sword.targetOrbitPathSpeed = 0.0
        sword.targetOrbitHitArmed = false
        sword.attackCooldownTicks = reacquireDelayTicks
    }

    private fun tickOwnerOrbit(
        sword: FlyingSword,
        destination: Location,
        tangent: Vector,
        scale: Float,
        returnAcceleration: Double,
        returnMaxSpeed: Double,
    ) {
        val wasReturning = sword.mode == SwordFlightMode.RETURNING
        sword.movementSpeed = if (wasReturning) {
            min(returnMaxSpeed, sword.movementSpeed + returnAcceleration)
        } else {
            returnMaxSpeed
        }
        val previous = sword.position.clone()
        moveSwordToward(sword, destination, sword.movementSpeed)
        val movement = sword.position.toVector().subtract(previous.toVector())
        val bladeDirection = if (movement.lengthSquared() > 1.0E-8) movement else tangent
        DisplayOrientationUtil.alignSwordBladeVertically(sword.display, bladeDirection, scale)

        if (
            wasReturning &&
            sword.position.toVector().distanceSquared(destination.toVector()) <= OWNER_ORBIT_CAPTURE_DISTANCE_SQUARED
        ) {
            sword.mode = SwordFlightMode.OWNER_ORBIT
            sword.movementSpeed = 0.0
        }
    }

    private fun moveSwordToward(sword: FlyingSword, destination: Location, maxDistance: Double) {
        if (destination.world != sword.position.world) {
            sword.position = destination.clone()
            sword.display.teleport(destination)
            return
        }
        val movement = destination.toVector().subtract(sword.position.toVector())
        if (movement.lengthSquared() > maxDistance * maxDistance) movement.normalize().multiply(maxDistance)
        sword.position = sword.position.clone().add(movement)
        sword.display.teleport(sword.position)
    }

    private fun teleportSwordPrecisely(sword: FlyingSword, destination: Location) {
        sword.position = destination.clone()
        sword.display.teleport(destination)
    }

    private fun spawnSwordDisplay(location: Location, material: Material, scale: Float): ItemDisplay {
        val display = location.world.spawn(location, ItemDisplay::class.java).apply {
            setItemStack(ItemStack(material))
            isPersistent = false
            billboard = Display.Billboard.FIXED
            brightness = Display.Brightness(15, 15)
            interpolationDuration = 1
            teleportDuration = 1
        }
        TemporaryDisplayManager.mark(display, player.uniqueId)
        DisplayOrientationUtil.alignSwordBladeHorizontally(display, Vector(1.0, 0.0, 0.0), scale)
        particles.spawn(location, Particle.END_ROD, count = 10, spread = 0.25, speed = 0.04)
        return display
    }

    private fun playSwordHit(target: EntityData, strong: Boolean) {
        particles.spawn(
            target.entity.boundingBox.center.toLocation(target.entity.world),
            Particle.SWEEP_ATTACK,
            count = if (strong) 3 else 1,
            spread = if (strong) 0.5 else 0.25,
            speed = 0.06,
        )
        particles.spawn(target.entity, Particle.CRIT, count = if (strong) 24 else 10, spread = 0.4, speed = 0.12)
        sounds.play(
            target.entity,
            if (strong) Sound.ENTITY_PLAYER_ATTACK_CRIT else Sound.ENTITY_PLAYER_ATTACK_SWEEP,
            volume = if (strong) 1.0f else 0.65f,
            pitch = if (strong) 0.82f else 1.32f,
        )
    }

    private fun clearInfinite(playEndEffect: Boolean) {
        infiniteTask?.cancel()
        infiniteTask = null
        infiniteSwords.forEach { it.display.remove() }
        infiniteSwords.clear()
        infiniteHitCounts.clear()
        if (!playEndEffect || !player.isOnline) return
        particles.spawn(player, Particle.REVERSE_PORTAL, count = 70, spread = 2.5, speed = 0.08)
        particles.spawn(player, Particle.END_ROD, count = 45, spread = 2.0, speed = 0.06)
        sounds.play(player, Sound.BLOCK_BEACON_DEACTIVATE, volume = 0.65f, pitch = 1.45f)
    }

    private fun resetSwordState() {
        passiveTask?.cancel()
        passiveTask = null
        baseSwords.forEach { it.display.remove() }
        baseSwords.clear()
        blossomActive = false
        clearInfinite(playEndEffect = false)
        passiveTick = 0
    }

    private class SwordsmanshipPassive : BasePassive() {
        override val name = "<bold>어검술"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>자신 주위를 날아다니는 검을 세 자루 생성한다.",
            "<gray>검은 사선의 구형 궤도로 공전하며, 자신 주위 6칸 내에 적이 접근하면 가속하여 1의 피해를 입힌다.",
            "<gray>타격한 검은 대상을 꿰뚫고 돌아오는 ∞ 궤도로 가속하며 계속 타격한다.",
        )
    }

    private companion object {
        const val PASSIVE_SWORD_COUNT = 3
        const val PASSIVE_TARGET_RADIUS = 6.0
        const val PASSIVE_TARGET_LEASH = 8.5
        const val PASSIVE_ORBIT_RADIUS = 1.8
        const val PASSIVE_ORBIT_CENTER_HEIGHT = 1.25
        const val PASSIVE_ORBIT_SPEED = 0.075
        const val BASE_RETURN_ACCELERATION = 0.045
        const val BASE_RETURN_MAX_SPEED = 0.72
        const val BASE_APPROACH_START_SPEED = 0.15
        const val BASE_APPROACH_ACCELERATION = 0.045
        const val BASE_APPROACH_MAX_SPEED = 0.95
        const val BASE_REACQUIRE_DELAY_TICKS = 10
        const val BASE_SWORD_HITBOX_EXPANSION = 0.32
        const val BASE_SWORD_SCALE = 1.35f
        const val BASE_TARGET_ORBIT_RADIUS = 4.0
        const val BASE_TARGET_ORBIT_START_PATH_SPEED = 0.18
        const val BASE_TARGET_ORBIT_PATH_ACCELERATION = 0.008
        const val BASE_TARGET_ORBIT_MAX_PATH_SPEED = 0.58

        const val BLOSSOM_DURATION_TICKS = 18
        const val BLOSSOM_ROTATION_SPEED = 0.58
        const val BLOSSOM_BLADE_HALF_LENGTH = 1.45
        const val BLOSSOM_HIT_RADIUS = 2.2
        const val BLOSSOM_HITBOX_EXPANSION = 0.35
        const val BLOSSOM_SWORD_SCALE = 1.7f

        const val INFINITE_SWORD_COUNT = 18
        const val INFINITE_DURATION_TICKS = 20 * 20
        const val INFINITE_HITS_PER_DAMAGE = 5
        const val INFINITE_TARGET_RADIUS = 7.0
        const val INFINITE_TARGET_LEASH = 9.0
        const val INFINITE_ORBIT_SPEED = 0.052
        const val INFINITE_ORBIT_SHELL_SPEED_STEP = 0.008
        const val INFINITE_ORBIT_SHELL_COUNT = 3
        const val INFINITE_ORBIT_PLANE_COUNT = 6
        const val INFINITE_ORBIT_PARTICLE_POINTS = 16
        const val INFINITE_ORBIT_INNER_RADIUS = 1.15
        const val INFINITE_ORBIT_RADIUS_STEP = 0.78
        const val INFINITE_ORBIT_CENTER_HEIGHT = 1.2
        const val INFINITE_RETURN_ACCELERATION = 0.052
        const val INFINITE_RETURN_MAX_SPEED = 0.9
        const val INFINITE_APPROACH_START_SPEED = 0.12
        const val INFINITE_APPROACH_ACCELERATION = 0.04
        const val INFINITE_APPROACH_MAX_SPEED = 1.05
        const val INFINITE_REACQUIRE_DELAY_TICKS = 8
        const val INFINITE_HITBOX_EXPANSION = 0.28
        const val INFINITE_SWORD_SCALE = 1.08f
        const val INFINITE_TARGET_ORBIT_RADIUS = 3.0
        const val INFINITE_TARGET_ORBIT_RADIUS_STEP = 0.28
        const val INFINITE_TARGET_ORBIT_START_PATH_SPEED = 0.16
        const val INFINITE_TARGET_ORBIT_PATH_ACCELERATION = 0.009
        const val INFINITE_TARGET_ORBIT_MAX_PATH_SPEED = 0.62

        const val TARGET_PIERCE_LOOP_WIDTH_RATIO = 0.74
        const val TARGET_PIERCE_BASE_ROLL = 0.35
        const val TARGET_PIERCE_ROLL_STEP = 0.83
        const val TARGET_PIERCE_REARM_DISTANCE_SQUARED = 0.36
        const val TARGET_PIERCE_MIN_DERIVATIVE_LENGTH = 0.001
        const val TARGET_PIERCE_MIN_ANGLE_STEP = 0.004
        const val TARGET_PIERCE_MAX_ANGLE_STEP = 0.16
        const val TARGET_PIERCE_MAX_ENTRY_PROJECTION = 0.35
        const val RETURN_RETAINED_SPEED_RATIO = 0.35
        const val RETURN_MIN_START_SPEED = 0.1
        const val OWNER_ORBIT_CAPTURE_DISTANCE_SQUARED = 0.0256
    }
}
