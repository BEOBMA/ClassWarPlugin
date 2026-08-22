package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.beobma.classWarPlugin.damage.DamageContext
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.sqrt

class Meteor : GameClass() {
    override val name = "<gray>메테오"
    override val rank = Rank.B
    override val classItemMaterial = Material.FIRE_CHARGE
    override var skills: List<Skill> = listOf(
        RedSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )
    private class RedSkill : Skill() {
        override val name = "<bold>유성 낙하"
        override val description = listOf(
            "<gray>18칸 내의 바라보는 위치에 2.5초 후 운석을 떨어트린다.",
            "<gray>적중한 모든 대상에게 중심부는 10, 외각은 거리에 비례하여 최소 5의 피해를 입히고 5초간 {keyword:Burn} 상태로 만든다.",
            "<gray>운석이 떨어진 위치에는 10초간 불타는 지형이 남으며, 지형 위의 적은 초당 1의 피해를 받고 {keyword:Burn} 지속 시간이 감소하지 않는다",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 자신의 위치에 시전할 수도 있다."
        )
        override val cooldown = 40

        override fun use() {
            val location = if (player.isSneaking) {
                player.location.clone()
            } else {
                playerData.shotLaserGetBlock(18.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return
                }
            }

            particles.circle(location, Particle.FLAME, 5.0, 48)
            sounds.play(location, Sound.ENTITY_BLAZE_AMBIENT, volume = 1.2f, pitch = 0.6f)

            val meteorStart = location.clone().add(-9.0, 28.0, -6.0)
            val meteorDisplay = location.world.spawn(meteorStart, ItemDisplay::class.java)
            meteorDisplay.setItemStack(ItemStack(Material.MAGMA_BLOCK))
            meteorDisplay.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            meteorDisplay.isPersistent = false
            TemporaryDisplayManager.mark(meteorDisplay, player.uniqueId)
            meteorDisplay.transformation = Transformation(
                Vector3f(), Quaternionf(), Vector3f(4.2f, 4.2f, 4.2f), Quaternionf()
            )
            playerData.trackTask(object : BukkitRunnable() {
                var tick = 0
                override fun run() {
                    if (tick > 50 || !meteorDisplay.isValid) {
                        meteorDisplay.remove()
                        cancel()
                        return
                    }
                    val progress = tick / 50.0
                    val eased = progress * progress
                    val current = meteorStart.clone().add(
                        (location.x - meteorStart.x) * eased,
                        (location.y - meteorStart.y) * eased,
                        (location.z - meteorStart.z) * eased,
                    )
                    meteorDisplay.teleport(current)
                    meteorDisplay.transformation = Transformation(
                        Vector3f(),
                        Quaternionf().rotateXYZ(tick * 0.09f, tick * 0.13f, tick * 0.07f),
                        Vector3f(4.2f, 4.2f, 4.2f),
                        Quaternionf(),
                    )
                    particles.spawn(current, Particle.FLAME, count = 5, spread = 0.8, speed = 0.04)
                    particles.spawn(current, Particle.LARGE_SMOKE, count = 2, spread = 0.65, speed = 0.02)
                    if (tick % 10 == 0) sounds.play(current, Sound.ENTITY_BLAZE_SHOOT, volume = 0.55f, pitch = 0.55f)
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))

            playerData.trackTask(
                object : BukkitRunnable() {
                    override fun run() {
                        particles.spawn(location, Particle.EXPLOSION_EMITTER, count = 2)
                        sounds.play(location, Sound.ENTITY_GENERIC_EXPLODE, volume = 2f, pitch = 0.55f)
                        val targets = playerData.radius(location, TargetType.Enemy, 5.0, false)
                        targets.forEach {
                            val distance = sqrt(HitboxUtil.distanceSquared(it.entity.boundingBox, location.toVector())).coerceAtMost(5.0)
                            it.damage((10.0 - distance).coerceAtLeast(5.0), DamageType.Normal, playerData)
                            it.entity.fireTicks += 100
                        }
                        var seconds = 0
                        playerData.trackTask(object : BukkitRunnable() {
                            override fun run() {
                                if (seconds++ >= 10) { cancel(); return }
                                particles.circle(location.clone().add(0.0, 0.12, 0.0), Particle.FLAME, 5.0, 48)
                                particles.spawn(location.clone().add(0.0, 0.3, 0.0), Particle.FLAME, count = 50, spread = 4.0, speed = 0.025)
                                particles.spawn(location.clone().add(0.0, 0.25, 0.0), Particle.SMOKE, count = 28, spread = 4.0, speed = 0.018)
                                particles.spawn(location.clone().add(0.0, 0.18, 0.0), Particle.LAVA, count = 8, spread = 3.6, speed = 0.02)
                                playerData.radius(location, TargetType.Enemy, 5.0, false).forEach {
                                    it.damage(1.0, DamageType.StatusAbnormality, playerData)
                                    it.entity.fireTicks = maxOf(it.entity.fireTicks, 40)
                                }
                            }
                        }.runTaskTimer(ClassWarPlugin.instance, 0L, 20L))
                    }
                }.runTaskLater(ClassWarPlugin.instance, 50L)
            )
        }

        override fun isUseSuccess(): Boolean {
            if (!player.isSneaking) {
                playerData.shotLaserGetBlock(18.0)?.location?.add(0.5, 1.0, 0.5) ?: run {
                    player.sendMiniMessage("<red><bold>[!] 바라보는 대상이 올바르지 않습니다.")
                    return false
                }
            }
            return true
        }
    }

    private class Passive : BasePassive(), WhenHitHandler {
        override val name = "<bold>화염 장막"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>기본 공격 피격 시 공격자를 2초간 {keyword:Burn} 상태로 만든다."
        )

        override fun whenAttackHit(event: DamageContext) {
            event.attacker.player.fireTicks += 40
        }
    }
}
