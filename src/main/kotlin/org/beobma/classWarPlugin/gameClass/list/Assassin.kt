package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.StatusDurationMode
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min

class Assassin : GameClass() {
    override val name = "<gray>암살자"
    override val description = listOf(
        "<dark_gray>근거리 암살자",
        "",
        "<gray>적의 후방에 잠입해 취약한 적을 제거합니다."
    )
    override val classItemMaterial = Material.NETHERITE_HELMET
    override val weapon = AssassinsDagger()

    override var skills: List<Skill> = listOf(
        AssassinsRedSkill(),
        AssassinsOrangeSkill(),
        AssassinsYellowSkill()
    )

    override var passives: List<Passive> = listOf(
        AssassinsPassive()
    )
}

class AssassinsDagger : Weapon() {
    override val name = ""
    override val description = listOf("")
    override val material = Material.AIR
}

class AssassinsRedSkill : Skill() {
    override val name = "<gray><bold>찌르기"
    override val description = listOf(
        "<gray>2칸 내의 바라보는 적에게 6의 피해를 입힌다.",
        "<gray>대상이 자신을 바라보고 있지 않았다면 3의 피해를 추가로 입힌다.",
        "",
        "<dark_gray>재사용 대기 시간: 10초"
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val targetData = playerData.shotLaserGetEntityData(2.0, TargetType.Enemy, false) ?: run {
            player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
            return false
        }
        targetData.damage(6.0, DamageType.Normal, playerData)
        val viewCheck = targetData.shotLaserGetEntityData(5.0, TargetType.All, false)
        if (viewCheck != playerData) {
            targetData.damage(3.0, DamageType.Normal, playerData)
        }
        if (playerData.hasStatus<Stealth>()) {
            player.setCooldown(Material.RED_DYE, (player.getCooldown(Material.RED_DYE) * 0.5).toInt())
        }
        return true
    }
}

class AssassinsOrangeSkill : Skill() {
    override val name = "<gray><bold>단검 투척"
    override val description = listOf(
        "<gray>바라보는 방향으로 단검을 투척한다.",
        "<gray>단검이 적에게 적중하면 5의 피해를 입히고 해당 적의 뒤로 즉시 이동한다.",
        "<gray>단검이 블록에 적중하면 4초간 {keyword:Stealth}하고 해당 방향으로 빠르게 이동한다.",
        "",
        "<dark_gray>재사용 대기 시간: 10초"
    )
    override val cooldown = 10

    override fun use(): Boolean {
        val assassinsDaggerProjectile = AssassinsDaggerProjectile()
        assassinsDaggerProjectile.location = player.eyeLocation.clone()
        assassinsDaggerProjectile.spawnProjectile(playerData)
        if (playerData.hasStatus<Stealth>()) {
            player.setCooldown(Material.ORANGE_DYE, (player.getCooldown(Material.YELLOW_DYE) * 0.5).toInt())
        }
        return true
    }
}

class AssassinsYellowSkill : Skill() {
    override val name = "<bold>암살"
    override val description = listOf(
        "<gray>2칸 내의 바라보는 적에게 10의 피해를 입힌다.",
        "<gray>자신이 {keyword:Stealth} 중이었다면 5의 피해를 추가로 입힌다.",
        "<gray>이 스킬로 적을 처치했다면 재사용 대기시간이 75% 감소한다.",
        "",
        "<dark_gray>재사용 대기 시간: 60초"
    )
    override val cooldown = 60

    override fun use(): Boolean {
        val targetData = playerData.shotLaserGetEntityData(2.0, TargetType.Enemy, false) ?: run {
            player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
            return false
        }
        targetData.damage(10.0, DamageType.Normal, playerData)
        if (playerData.hasStatus<Stealth>()) {
            targetData.damage(5.0, DamageType.Normal, playerData)
            player.setCooldown(Material.YELLOW_DYE, (player.getCooldown(Material.YELLOW_DYE) * 0.5).toInt())
        }
        if (targetData.entityStatus.isDead) {
            player.setCooldown(Material.YELLOW_DYE, (player.getCooldown(Material.YELLOW_DYE) * 0.25).toInt())
        }
        return true
    }
}

class AssassinsPassive : Passive() {
    override val name = "<bold>암살자의 각오"
    override val description = listOf(
        "<gray>패시브",
        "",
        "<gray>{keyword:Stealth} 상태에서 사용한 스킬의 재사용 대기 시간이 50% 감소한다."
    )
}

class AssassinsDaggerProjectile : Projectile() {
    override lateinit var location: Location
    override var targetType: TargetType = TargetType.Enemy
    override var speed: Double = 1.0
    override var isWallHit: Boolean = true
    override var isPlayerHit: Boolean = true
    override val isPlayerHitRemove: Boolean = true

    override fun onProjectileMove(location: Location) {
        location.world.spawnParticle(Particle.END_ROD, location, 1, 0.0, 0.0, 0.0, 0.0)
    }

    override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
        val hitEntity = hitEntityData.entity
        val hitEntityLocation = hitEntity.location
        val behind = hitEntityLocation.clone().add(hitEntityLocation.direction.normalize().multiply(-1.5))
        hitEntityData.damage(5.0, DamageType.Normal, playerData)
        player.teleport(behind)
    }

    override fun onProjectileBlockHit(hitBlock: Block, location: Location) {
        val hitPoint = location.clone()

        val toHit = hitPoint.toVector().subtract(player.location.toVector())
        if (toHit.lengthSquared() < 1.0E-6) return

        val dir = toHit.clone().normalize()
        val target = hitPoint.clone().add(dir.clone().multiply(-0.35))

        val stealth = playerData.getOrCreateStatus { Stealth() }
        stealth.applyStatus(
            duration = 4,
            durationMode = StatusDurationMode.Extend,
            powerDelta = 1
        )

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
                    stop()
                    return
                }

                val delta = target.toVector().subtract(player.location.toVector())
                val dist = delta.length()

                if (dist <= stopDistance) {
                    stop()
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
}
