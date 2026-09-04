package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getConeTargets
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
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

// 밸런스 조정 상수
private const val WEAPON_MASTER_SCIMITAR_COOLDOWN_SECONDS = 4
private const val WEAPON_MASTER_SHOTGUN_COOLDOWN_SECONDS = 5
private const val WEAPON_MASTER_CLAW_COOLDOWN_SECONDS = 3
private const val WEAPON_MASTER_BRICK_COOLDOWN_SECONDS = 8
private const val WEAPON_MASTER_BOMB_COOLDOWN_SECONDS = 8
private const val WEAPON_MASTER_KNIFE_COOLDOWN_SECONDS = 5
private const val WEAPON_MASTER_WING_DAGGER_COOLDOWN_SECONDS = 10
private const val WEAPON_MASTER_NEEDLE_BAT_COOLDOWN_SECONDS = 12
private const val WEAPON_MASTER_SCIMITAR_DAMAGE = 2.0
private const val WEAPON_MASTER_SHOTGUN_DAMAGE = 3.0
private const val WEAPON_MASTER_CLAW_DAMAGE = 2.0
private const val WEAPON_MASTER_BRICK_DAMAGE = 4.0
private const val WEAPON_MASTER_BOMB_DAMAGE = 4.0
private const val WEAPON_MASTER_KNIFE_DAMAGE = 5.0
private const val WEAPON_MASTER_WING_DAGGER_DAMAGE = 2.0
private const val WEAPON_MASTER_WING_DAGGER_EXPLOSION_DAMAGE = 3.0
private const val WEAPON_MASTER_NEEDLE_BAT_START_DAMAGE = 2.0
private const val WEAPON_MASTER_NEEDLE_BAT_DAMAGE_PER_COMBO = 1.0
private const val WEAPON_MASTER_NEEDLE_BAT_MAX_COMBO = 3
private const val WEAPON_MASTER_MASTERY_DAMAGE_PER_STACK = 0.1
private const val WEAPON_MASTER_MAX_MASTERY_STACKS = 8
private const val WEAPON_MASTER_MASTERY_DURATION_SECONDS = 6
private const val WEAPON_MASTER_CHAIN_SHIELD_DURATION_SECONDS = 4
private const val WEAPON_MASTER_CHAIN_SHIELD_POWER = 2
private const val WEAPON_MASTER_CHAIN_BURST_DAMAGE = 4.0

class WeaponMaster : GameClass(), GameStatusHandler {
    override val classId = "weapon-master"
    override val name = "<gray>웨폰마스터"
    override val rank = Rank.L
    override val classItemMaterial = Material.MUSIC_DISC_BOUNCE

    private val scimitar = ScimitarSkill()
    private val shotgun = ShotgunSkill()
    private val claw = ClawSkill()
    private val brick = BrickSkill()
    private val bomb = BombSkill()
    private val knife = KnifeSkill()
    private val wingDagger = WingDaggerSkill()
    private val needleBat = NeedleBatSkill()

    override var skills: List<Skill> = listOf(scimitar, shotgun, claw, brick, bomb, knife, wingDagger, needleBat)
    override var passives: List<BasePassive> = listOf(WeaponChainPassive())

    private var lastBasicAttackHitTick = Long.MIN_VALUE
    private var chainBurstActive = false
    private var applyingChainBurstDamage = false

    override fun onBattleStart() {
        lastBasicAttackHitTick = Long.MIN_VALUE
        chainBurstActive = false
        applyingChainBurstDamage = false
        playerData.getStatus<WeaponMasteryStatus>()?.remove()
        wingDagger.reset()
        needleBat.reset()
    }

    override fun onGameTimePasses() = Unit

    private fun returnToWeapon() {
        player.inventory.heldItemSlot = if (playerData.gameClasses.indexOf(this) == 1) 8 else 0
    }

    private fun horizontalDirection(): Vector {
        val direction = player.eyeLocation.direction.setY(0.0)
        return if (direction.lengthSquared() < 1.0E-8) Vector(0.0, 0.0, 1.0) else direction.normalize()
    }

    private fun isGrounded(): Boolean {
        val box = player.boundingBox
        val foot = Vector(box.center.x, box.minY + 0.06, box.center.z).toLocation(player.world)
        return player.world.rayTraceBlocks(foot, Vector(0.0, -1.0, 0.0), 0.16)?.hitBlock?.type?.isSolid == true
    }

    private fun skillDamage(target: org.beobma.classWarPlugin.entity.EntityData, amount: Double) {
        target.damage(amount, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
    }

    private inner class ScimitarSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "weapon-master/scimitar-skill"
        override val name = "<bold>시미터"
        override val description = listOf(
            "<gray>바라보는 방향으로 돌진하여 모든 적을 베어 2의 피해를 입힌다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_SCIMITAR_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val direction = horizontalDirection()
            player.velocity = direction.clone().multiply(1.45).setY(0.16)
            val hit = mutableSetOf<UUID>()
            var previous = player.boundingBox.center
            var tick = 0
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (!player.isOnline || playerStatus.isDead || tick++ >= 8) {
                        cancel()
                        return
                    }
                    val current = player.boundingBox.center
                    playerData.radius(current.toLocation(player.world), TargetType.Enemy, 2.2, false, hitAttackableObjects = true).forEach { target ->
                        if (target.entity.uniqueId in hit) return@forEach
                        if (!HitboxUtil.intersectsSegment(target.entity.boundingBox, previous, current, 0.85)) return@forEach
                        hit += target.entity.uniqueId
                        skillDamage(target, WEAPON_MASTER_SCIMITAR_DAMAGE)
                        particles.spawn(target.entity, Particle.SWEEP_ATTACK, count = 1)
                    }
                    particles.spawn(current.toLocation(player.world), Particle.SWEEP_ATTACK, count = 1, spread = 0.12)
                    particles.spawn(current.toLocation(player.world), Particle.CLOUD, count = 3, spread = 0.18, speed = 0.03)
                    previous = current
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 1.0f, pitch = 1.35f)
            sounds.play(player, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.55f, pitch = 1.6f)
            returnToWeapon()
            return true
        }
    }

    private inner class ShotgunSkill : Skill() {
        override val definitionId = "weapon-master/shotgun-skill"
        override val name = "<bold>샷건"
        override val description = listOf(
            "<gray>바라보는 방향으로 샷건을 발사하여 3의 피해를 입힌 뒤 자신은 후방으로 밀려난다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_SHOTGUN_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val eye = player.eyeLocation
            val direction = eye.direction.normalize()
            var sideDirection = direction.clone().crossProduct(Vector(0.0, 1.0, 0.0))
            if (sideDirection.lengthSquared() < 1.0E-8) sideDirection = Vector(1.0, 0.0, 0.0)
            sideDirection.normalize()
            val shotgunRange = ClassBalanceManager.scaleRange(playerData, 8.0)
            val blockDistance = player.world.rayTraceBlocks(eye, direction, shotgunRange)?.hitPosition
                ?.distance(eye.toVector()) ?: shotgunRange
            playerData.getConeTargets(8.0, 42.0, TargetType.Enemy, false, hitAttackableObjects = true).forEach { target ->
                val closest = HitboxUtil.closestPoint(target.entity.boundingBox, eye.toVector())
                if (closest.distance(eye.toVector()) <= blockDistance + 0.3) {
                    skillDamage(target, WEAPON_MASTER_SHOTGUN_DAMAGE)
                }
            }
            repeat(11) { pellet ->
                val spread = (pellet - 5) * 0.035
                val pelletDirection = direction.clone().add(sideDirection.clone().multiply(spread))
                    .add(Vector(0.0, (pellet % 3 - 1) * 0.025, 0.0)).normalize()
                particles.line(eye, eye.clone().add(pelletDirection.multiply(blockDistance)), Particle.SMOKE, spacing = 0.8)
            }
            particles.spawn(eye, Particle.FLASH, count = 1)
            particles.spawn(eye, Particle.CLOUD, count = 28, spread = 0.3, speed = 0.16)
            sounds.play(eye, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.85f, pitch = 1.65f)
            sounds.play(eye, Sound.ITEM_CROSSBOW_SHOOT, volume = 1.0f, pitch = 0.62f)
            player.velocity = direction.clone().multiply(-0.85).setY(0.28)
            returnToWeapon()
            return true
        }
    }

    private inner class ClawSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "weapon-master/claw-skill"
        override val name = "<bold>클로"
        override val description = listOf(
            "<gray>공중에서만 사용할 수 있다.", "",
            "<gray>전방 사선 방향으로 하강하며 적중한 적에게 2의 피해를 입힌다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_CLAW_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (!isGrounded()) return true
            player.sendMiniMessage("<red><bold>[!] 클로는 공중에서만 사용할 수 있습니다.")
            return false
        }

        override fun use(): Boolean {
            val direction = horizontalDirection()
            player.velocity = direction.clone().multiply(1.05).setY(-0.85)
            val hit = mutableSetOf<UUID>()
            var previous = player.boundingBox.center
            var tick = 0
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (!player.isOnline || playerStatus.isDead || tick >= 11 || (tick > 2 && isGrounded())) {
                        cancel()
                        return
                    }
                    val current = player.boundingBox.center
                    playerData.radius(current.toLocation(player.world), TargetType.Enemy, 2.0, false, hitAttackableObjects = true).forEach { target ->
                        if (target.entity.uniqueId in hit) return@forEach
                        if (!HitboxUtil.intersectsSegment(target.entity.boundingBox, previous, current, 0.75)) return@forEach
                        hit += target.entity.uniqueId
                        skillDamage(target, WEAPON_MASTER_CLAW_DAMAGE)
                        particles.spawn(target.entity, Particle.CRIT, count = 14, spread = 0.35, speed = 0.08)
                    }
                    particles.spawn(current.toLocation(player.world), Particle.SWEEP_ATTACK, count = 1, spread = 0.08)
                    previous = current
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.85f, pitch = 1.55f)
            returnToWeapon()
            return true
        }
    }

    private inner class BrickSkill : Skill() {
        override val definitionId = "weapon-master/brick-skill"
        override val name = "<bold>브릭"
        override val description = listOf(
            "<gray>전방을 향해 짧게 점프하며 벽돌을 내려 찍어 모든 적에게 4의 피해를 입힌다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_BRICK_COOLDOWN_SECONDS

        override fun use(): Boolean {
            player.velocity = horizontalDirection().multiply(0.58).setY(0.72)
            var tick = 0
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (!player.isOnline || playerStatus.isDead) {
                        cancel()
                        return
                    }
                    particles.spawn(player.location, Particle.BLOCK, Material.BRICKS.createBlockData(), org.beobma.classWarPlugin.effect.ParticleOptions.spread(2, 0.18, 0.02))
                    if ((tick >= 4 && isGrounded()) || tick >= 22) {
                        slam(player.location)
                        cancel()
                        return
                    }
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, volume = 0.65f, pitch = 0.7f)
            returnToWeapon()
            return true
        }

        private fun slam(location: Location) {
            playerData.radius(location, TargetType.Enemy, 3.25, false, hitAttackableObjects = true)
                .forEach { skillDamage(it, WEAPON_MASTER_BRICK_DAMAGE) }
            particles.spawn(location, Particle.BLOCK, Material.BRICKS.createBlockData(), org.beobma.classWarPlugin.effect.ParticleOptions.spread(90, 2.2, 0.24))
            particles.circle(location.clone().add(0.0, 0.12, 0.0), Particle.DUST_PLUME, 3.25, 52)
            particles.spawn(location, Particle.EXPLOSION, count = 4, spread = 1.2, speed = 0.05)
            sounds.play(location, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.9f, pitch = 0.72f)
            sounds.play(location, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, volume = 1.15f, pitch = 0.65f)
        }
    }

    private inner class BombSkill : Skill() {
        override val definitionId = "weapon-master/bomb-skill"
        override val name = "<bold>봄"
        override val description = listOf(
            "<gray>폭탄을 던진 뒤 후방으로 도주한다.",
            "<gray>이후 폭탄이 폭발하여 모든 적에게 4의 피해를 입힌다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_BOMB_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val direction = player.eyeLocation.direction.normalize()
            val start = player.eyeLocation.clone().add(direction.clone().multiply(0.6))
            val display = start.world.spawn(start, BlockDisplay::class.java).apply {
                block = Material.TNT.createBlockData()
                isPersistent = false
                brightness = Display.Brightness(13, 13)
                transformation = Transformation(
                    Vector3f(-0.3f, -0.3f, -0.3f), Quaternionf(), Vector3f(0.6f, 0.6f, 0.6f), Quaternionf()
                )
                TemporaryDisplayManager.mark(this, player.uniqueId)
            }
            val location = start.clone()
            val velocity = direction.clone().multiply(0.8).setY(0.52)
            player.velocity = direction.clone().multiply(-0.8).setY(0.3)
            var stuck = false
            var tick = 0
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (!player.isOnline || playerStatus.isDead) {
                        display.remove()
                        cancel()
                        return
                    }
                    if (!stuck) {
                        val next = location.clone().add(velocity)
                        if (next.block.type.isSolid) {
                            stuck = true
                        } else {
                            location.add(velocity)
                            velocity.y -= 0.055
                            display.teleport(location)
                        }
                    }
                    particles.spawn(location, if (tick % 2 == 0) Particle.FLAME else Particle.SMOKE, count = 2, spread = 0.12, speed = 0.015)
                    if (tick % 7 == 0) sounds.play(location, Sound.BLOCK_NOTE_BLOCK_HAT, volume = 0.45f, pitch = 1.5f + tick * 0.015f)
                    if (tick++ >= 30) {
                        display.remove()
                        explode(location)
                        cancel()
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            sounds.play(start, Sound.ENTITY_TNT_PRIMED, volume = 0.8f, pitch = 1.25f)
            returnToWeapon()
            return true
        }

        private fun explode(location: Location) {
            playerData.radius(location, TargetType.Enemy, 3.6, false, hitAttackableObjects = true)
                .forEach { skillDamage(it, WEAPON_MASTER_BOMB_DAMAGE) }
            particles.spawn(location, Particle.EXPLOSION_EMITTER, count = 2, spread = 0.8)
            particles.spawn(location, Particle.FLAME, count = 70, spread = 2.2, speed = 0.2)
            particles.spawn(location, Particle.SMOKE, count = 55, spread = 2.0, speed = 0.14)
            sounds.play(location, Sound.ENTITY_GENERIC_EXPLODE, volume = 1.2f, pitch = 0.85f)
        }
    }

    private inner class KnifeSkill : Skill() {
        override val definitionId = "weapon-master/knife-skill"
        override val name = "<bold>나이프"
        override val description = listOf(
            "<gray>전후방을 베어내어 모든 적에게 5의 피해를 입힌다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_KNIFE_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val origin = player.boundingBox.center
            val forward = horizontalDirection()
            val threshold = cos(Math.toRadians(58.0))
            playerData.radius(origin.toLocation(player.world), TargetType.Enemy, 4.0, false, hitAttackableObjects = true).forEach { target ->
                val closest = HitboxUtil.closestPoint(target.entity.boundingBox, origin)
                val relative = closest.subtract(origin)
                if (relative.lengthSquared() <= 1.0E-8 || abs(forward.dot(relative.normalize())) >= threshold) {
                    skillDamage(target, WEAPON_MASTER_KNIFE_DAMAGE)
                }
            }
            repeat(2) { side ->
                repeat(18) { index ->
                    val angle = Math.toRadians(-58.0 + 116.0 * index / 17.0) + if (side == 0) 0.0 else PI
                    val x = forward.x * cos(angle) - forward.z * sin(angle)
                    val z = forward.x * sin(angle) + forward.z * cos(angle)
                    particles.spawn(origin.toLocation(player.world).add(x * 3.2, 0.25, z * 3.2), Particle.SWEEP_ATTACK, count = 1)
                }
            }
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 1.2f, pitch = 0.65f)
            sounds.play(player, Sound.ITEM_TRIDENT_THROW, volume = 0.65f, pitch = 1.55f)
            returnToWeapon()
            return true
        }
    }

    private inner class WingDaggerSkill : Skill() {
        override val definitionId = "weapon-master/wing-dagger-skill"
        override val name = "<bold>윙대거"
        override val description = listOf(
            "<gray>일정 시간 후 폭발하는 수리검을 던진다. 재사용하면 폭발시킬 수 있다.",
            "<gray>닿은 적에게는 2의 피해를, 폭발에 닿으면 3의 피해를 입는다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_WING_DAGGER_COOLDOWN_SECONDS

        private var activeDisplay: ItemDisplay? = null
        private var activeLocation: Location? = null
        private var activeTask: BukkitTask? = null
        private var cooldownItem: ItemStack? = null

        override fun use(): Boolean {
            if (activeDisplay != null) {
                explode(startCooldown = false)
                returnToWeapon()
                return true
            }
            cooldownItem = player.inventory.itemInMainHand.clone()
            multiplyCurrentCooldown(0.0)
            launch()
            returnToWeapon()
            return true
        }

        private fun launch() {
            val location = player.eyeLocation.clone().add(player.eyeLocation.direction.normalize().multiply(0.6))
            val velocity = player.eyeLocation.direction.normalize().multiply(1.05)
            val display = location.world.spawn(location, ItemDisplay::class.java).apply {
                setItemStack(ItemStack(Material.NETHER_STAR))
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(1.15f, 1.15f, 1.15f), Quaternionf())
                TemporaryDisplayManager.mark(this, player.uniqueId)
            }
            activeDisplay = display
            activeLocation = location
            var stuck = false
            var tick = 0
            activeTask = playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (activeDisplay !== display || !player.isOnline || playerStatus.isDead) {
                        cleanup()
                        cancel()
                        return
                    }
                    val current = activeLocation ?: run {
                        cleanup()
                        cancel()
                        return
                    }
                    if (!stuck) {
                        val next = current.clone().add(velocity)
                        val hitTarget = playerData.radius(next, TargetType.Enemy, 1.3, false, hitAttackableObjects = true)
                            .firstOrNull { HitboxUtil.intersectsSegment(it.entity.boundingBox, current.toVector(), next.toVector(), 0.35) }
                        if (hitTarget != null) {
                            skillDamage(hitTarget, WEAPON_MASTER_WING_DAGGER_DAMAGE)
                            particles.spawn(hitTarget.entity, Particle.CRIT, count = 14, spread = 0.35, speed = 0.08)
                            sounds.play(hitTarget.entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.75f, pitch = 1.45f)
                            stuck = true
                        } else if (next.block.type.isSolid) {
                            stuck = true
                            sounds.play(current, Sound.BLOCK_CHAIN_PLACE, volume = 0.6f, pitch = 1.7f)
                        } else {
                            current.add(velocity)
                            activeLocation = current
                        }
                    }
                    display.teleport(current)
                    display.transformation = Transformation(
                        Vector3f(), Quaternionf().rotateXYZ(tick * 0.13f, tick * 0.38f, tick * 0.3f),
                        Vector3f(1.15f, 1.15f, 1.15f), Quaternionf()
                    )
                    particles.spawn(current, Particle.END_ROD, count = 1, spread = 0.08)
                    if (tick++ >= 50) {
                        explode(startCooldown = true)
                        cancel()
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            sounds.play(location, Sound.ITEM_TRIDENT_THROW, volume = 0.9f, pitch = 1.8f)
        }

        private fun explode(startCooldown: Boolean) {
            val location = activeLocation?.clone() ?: return
            activeTask?.cancel()
            activeTask = null
            activeDisplay?.remove()
            activeDisplay = null
            activeLocation = null
            playerData.radius(location, TargetType.Enemy, 3.2, false, hitAttackableObjects = true)
                .forEach { skillDamage(it, WEAPON_MASTER_WING_DAGGER_EXPLOSION_DAMAGE) }
            particles.spawn(location, Particle.EXPLOSION, count = 4, spread = 1.2, speed = 0.08)
            particles.spawn(location, Particle.END_ROD, count = 55, spread = 2.0, speed = 0.18)
            particles.spawn(location, Particle.CRIT, count = 45, spread = 1.8, speed = 0.16)
            sounds.play(location, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.95f, pitch = 1.35f)
            if (startCooldown) {
                CooldownManager.setCooldown(player, this, cooldownItem ?: ItemStack(Material.PINK_DYE), cooldown * 20)
            }
        }

        private fun cleanup() {
            activeTask?.cancel()
            activeTask = null
            activeDisplay?.remove()
            activeDisplay = null
            activeLocation = null
        }

        fun reset() = cleanup()
    }

    private inner class NeedleBatSkill : Skill() {
        override val definitionId = "weapon-master/needle-bat-skill"
        override val name = "<bold>니들배트"
        override val description = listOf(
            "<gray>전방을 향해 방망이를 내려찍는다.",
            "<gray>최대 3번까지 연속으로 사용 가능하며, 각각 2, 3, 4의 피해를 입힌다.", "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = WEAPON_MASTER_NEEDLE_BAT_COOLDOWN_SECONDS

        private var combo = 0
        private var comboToken = 0
        private var cooldownItem: ItemStack? = null

        override fun use(): Boolean {
            cooldownItem = player.inventory.itemInMainHand.clone()
            combo = (combo + 1).coerceAtMost(WEAPON_MASTER_NEEDLE_BAT_MAX_COMBO)
            val step = combo
            val damage = WEAPON_MASTER_NEEDLE_BAT_START_DAMAGE +
                (step - 1) * WEAPON_MASTER_NEEDLE_BAT_DAMAGE_PER_COMBO
            playerData.getConeTargets(3.8, 82.0, TargetType.Enemy, false, hitAttackableObjects = true).forEach { skillDamage(it, damage) }
            drawBatSwing(step)
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_STRONG, volume = 0.85f, pitch = 1.2f - step * 0.14f)
            if (step < WEAPON_MASTER_NEEDLE_BAT_MAX_COMBO) {
                multiplyCurrentCooldown(0.0)
                scheduleComboTimeout()
            } else {
                combo = 0
                comboToken++
                particles.spawn(player, Particle.EXPLOSION, count = 2, spread = 0.55, speed = 0.04)
            }
            returnToWeapon()
            return true
        }

        private fun drawBatSwing(step: Int) {
            val center = player.boundingBox.center.toLocation(player.world)
            val forward = horizontalDirection()
            repeat(20) { index ->
                val angle = Math.toRadians(-45.0 + 90.0 * index / 19.0)
                val x = forward.x * cos(angle) - forward.z * sin(angle)
                val z = forward.x * sin(angle) + forward.z * cos(angle)
                val point = center.clone().add(x * (2.2 + step * 0.25), 1.0 - index / 19.0 * 1.2, z * (2.2 + step * 0.25))
                particles.spawn(point, if (step == 3) Particle.CRIT else Particle.SWEEP_ATTACK, count = 1)
            }
        }

        private fun scheduleComboTimeout() {
            val token = ++comboToken
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (token != comboToken || combo == 0) return
                    combo = 0
                    val item = cooldownItem ?: ItemStack(Material.GRAY_DYE)
                    CooldownManager.setCooldown(player, this@NeedleBatSkill, item, cooldown * 20)
                }
            }.runTaskLater(ClassWarPlugin.instance, 24L))
        }

        fun reset() {
            combo = 0
            comboToken++
        }
    }

    private inner class WeaponChainPassive : BasePassive(), OnHitHandler, OnSkillUseHandler {
        override val name = "<bold>무기 연계"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>기본 공격 적중 후 1초 이내에 스킬을 사용하면 6초간 달인 스택을 1 얻는다.",
            "<gray>달인 스택 1당 스킬의 최종 피해량이 10% 증가한다. (최대 80%)",
            "<gray>달인 스택을 얻을 때, 4초 동안 <aqua><bold>2의 피해를 막는 {keyword:Shield}을 얻는다.", "",
            "<gray>달인 스택이 최대치일 때 달인 스택을 얻으면",
            "<gray>주변에 사슬을 던진 후 회수하여 적중한 모든 적에게 4의 피해를 입힌다.", "",
            "<dark_gray>달인 스택을 얻을 때마다 지속 시간이 갱신된다."
        )

        override fun onAttackHit(context: DamageContext) {
            lastBasicAttackHitTick = game.combatTick
        }

        override fun onSkillAttackHit(context: DamageContext) {
            if (applyingChainBurstDamage) return
            val stacks = playerData.getStatus<WeaponMasteryStatus>()?.power ?: 0
            if (stacks > 0) {
                context.addDamageDealtMultiplier(1.0 + stacks * WEAPON_MASTER_MASTERY_DAMAGE_PER_STACK)
            }
        }

        override fun onSkillUse(event: PlayerSkillUseEvent) {
            val elapsed = game.combatTick - lastBasicAttackHitTick
            if (elapsed !in 0L..20L) return
            gainMasteryStack()
        }
    }

    private fun gainMasteryStack() {
        val status = playerData.getOrCreateStatus(playerData) { WeaponMasteryStatus() }
        val wasMaximum = status.power >= 8
        status.applyStatus(
            duration = WEAPON_MASTER_MASTERY_DURATION_SECONDS,
            powerSet = (status.power + 1).coerceAtMost(WEAPON_MASTER_MAX_MASTERY_STACKS),
        )
        playerData.addStatus(Shield(), playerData).applyStatus(
            duration = WEAPON_MASTER_CHAIN_SHIELD_DURATION_SECONDS,
            powerSet = WEAPON_MASTER_CHAIN_SHIELD_POWER,
        )
        particles.spawn(
            player.boundingBox.center.toLocation(player.world),
            Particle.DUST,
            Particle.DustOptions(Color.fromRGB(70, 220, 255), 1.25f),
            org.beobma.classWarPlugin.effect.ParticleOptions.spread(18, 0.5, 0.06),
        )
        sounds.play(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.65f, pitch = 1.1f + status.power * 0.08f)
        if (wasMaximum) triggerChainBurst()
    }

    private fun triggerChainBurst() {
        if (chainBurstActive) return
        chainBurstActive = true
        val origin = player.boundingBox.center.toLocation(player.world)
        var tick = 0
        sounds.play(origin, Sound.BLOCK_CHAIN_PLACE, volume = 1.0f, pitch = 0.7f)
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead || tick > 12) {
                    chainBurstActive = false
                    cancel()
                    return
                }
                val progress = if (tick <= 6) tick / 6.0 else (12 - tick) / 6.0
                val radius = 6.2 * progress.coerceAtLeast(0.0)
                repeat(12) { spoke ->
                    val angle = 2.0 * PI * spoke / 12.0
                    val end = origin.clone().add(cos(angle) * radius, 0.1, sin(angle) * radius)
                    particles.line(
                        origin,
                        end,
                        Particle.BLOCK,
                        Material.IRON_CHAIN.createBlockData(),
                        spacing = 0.65,
                    )
                }
                if (tick == 7) {
                    val hit = mutableSetOf<UUID>()
                    playerData.radius(origin, TargetType.Enemy, 6.5, false, hitAttackableObjects = true).forEach { target ->
                        val intersects = (0 until 12).any { spoke ->
                            val angle = 2.0 * PI * spoke / 12.0
                            val end = origin.toVector().add(Vector(cos(angle) * 6.2, 0.1, sin(angle) * 6.2))
                            HitboxUtil.intersectsSegment(target.entity.boundingBox, origin.toVector(), end, 0.42)
                        }
                        if (!intersects || !hit.add(target.entity.uniqueId)) return@forEach
                        applyingChainBurstDamage = true
                        try {
                            target.damage(
                                WEAPON_MASTER_CHAIN_BURST_DAMAGE,
                                DamageType.Normal,
                                playerData,
                                damagePath = DamagePath.SKILL,
                            )
                        } finally {
                            applyingChainBurstDamage = false
                        }
                        particles.spawn(target.entity, Particle.CRIT, count = 16, spread = 0.4, speed = 0.09)
                    }
                    sounds.play(origin, Sound.BLOCK_CHAIN_BREAK, volume = 1.15f, pitch = 0.62f)
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }
}

private class WeaponMasteryStatus : StatusAbnormality() {
    override val name = "<aqua><bold>달인</bold><gray>"
    override val description = listOf("<gray>스택당 웨폰마스터의 스킬 최종 피해량이 10% 증가한다.")
    override val canRemove = true
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = 8
    override var duration: Int? = 6
}
