package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.Weapon as BaseWeapon
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min

class Assassin : GameClass() {
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


    private class Weapon : BaseWeapon() {
        override val name = "단검"
        override val description = listOf("")
        override val material = Material.IRON_SWORD
    }

    private class RedSkill : Skill() {
        override val name = "<bold>단검 투척"
        override val description = listOf(
            "<gray>바라보는 방향으로 단검을 투척한다.",
            "<gray>단검이 적에게 적중하면 5의 피해를 입히고 해당 적의 뒤로 즉시 이동한다.",
            "<gray>단검이 블록에 적중하면 {keyword:Stealth} 상태가 되고, 해당 블록으로 날아가 벽에 붙는다.",
            "<gray>벽에 붙은 상태에서 행동하면 벽에서 떨어진다.",
            "",
            "<dark_gray>이 스킬을 사용한 후, 최초 1회의 낙하 피해는 무효화되며, 벽에서 떨어진 후 6초간 {keyword:Stealth} 상태가 유지된다.",
        )
        override val cooldown = 30

        override fun use() {
            val assassinsDaggerProjectile = DaggerProjectile()
            assassinsDaggerProjectile.location = player.eyeLocation.clone()
            assassinsDaggerProjectile.spawnProjectile(playerData)
            sounds.play(player, Sound.ENTITY_SKELETON_SHOOT, pitch = 2f)
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>암살"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 적중 시, 대상이 자신을 바라보고 있지 않았다면 피해량이 2 증가한다."
        )
    }

    private class DaggerProjectile : Projectile() {
        override lateinit var location: Location
        override var targetType: TargetType = TargetType.Enemy
        override var speed: Double = 1.0
        override var isWallHit: Boolean = true
        override var isPlayerHit: Boolean = true
        override val isPlayerHitRemove: Boolean = true
        override val itemDisplayItem: ItemStack = ItemStack(Material.IRON_SWORD)

        override fun onItemDisplaySpawn(display: ItemDisplay, location: Location) {
            display.setRotation(location.yaw, location.pitch)
        }

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            val hitEntity = hitEntityData.entity
            val hitEntityLocation = hitEntity.location
            val behind = hitEntityLocation.clone().add(hitEntityLocation.direction.normalize().multiply(-1.5))
            hitEntityData.damage(5.0, DamageType.Normal, playerData)
            particles.spawn(player.location, Particle.SMOKE, count = 10)
            particles.spawn(behind, Particle.SMOKE, count = 10)
            player.teleport(behind)
            sounds.play(player, Sound.ITEM_TRIDENT_RETURN)
        }

        override fun onProjectileBlockHit(hitBlock: Block, location: Location) {
            val hitPoint = location.clone()
            sounds.play(hitPoint, Sound.ITEM_TRIDENT_RETURN)

            val toHit = hitPoint.toVector().subtract(player.location.toVector())
            if (toHit.lengthSquared() < 1.0E-6) return

            val dir = toHit.clone().normalize()
            val target = hitPoint.clone().add(dir.clone().multiply(-0.35))

            val stealth = playerData.getOrCreateStatus(playerData) { Stealth() }
            stealth.applyStatus(
                duration = 4,
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
}
