package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Barrier : GameClass() {
    override val name = "<gray>방벽"
    override val rank = Rank.C
    override val classItemMaterial = Material.SHIELD
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf()

    private inner class RedSkill : Skill(), WhenHitHandler {
        override val name = "<bold>방패 세우기"
        override val description = listOf(
            "<gray>4초간 방패를 들어 바라보는 방향에서 <gold><bold>받는 피해를 60% 감소</bold><gray>시킨다."
        )
        override val cooldown = 30

        private var activeUntilTick = 0L
        private var lastBlockSoundTick = Long.MIN_VALUE

        override fun use() {
            activeUntilTick = player.world.fullTime + 80L
            val display = player.world.spawn(player.location, ItemDisplay::class.java).apply {
                setItemStack(ItemStack(Material.SHIELD))
                itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                isPersistent = false
                transformation = Transformation(
                    Vector3f(),
                    Quaternionf().rotateY(PI.toFloat()),
                    Vector3f(1.35f, 1.35f, 1.35f),
                    Quaternionf(),
                )
                TemporaryDisplayManager.mark(this, player.uniqueId)
            }
            sounds.play(player, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 1.0f, pitch = 0.75f)

            playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline || player.isDead || player.world.fullTime >= activeUntilTick) {
                        display.remove()
                        sounds.play(player, Sound.ITEM_SHIELD_BLOCK, volume = 0.7f, pitch = 0.65f)
                        cancel()
                        return
                    }
                    val forward = player.eyeLocation.direction.normalize()
                    val displayLocation = player.location.clone().add(0.0, 1.15, 0.0)
                        .add(forward.clone().multiply(0.72))
                    displayLocation.yaw = player.location.yaw + 180.0f
                    displayLocation.pitch = 0.0f
                    display.teleport(displayLocation)

                    if (player.world.fullTime % 2L == 0L) {
                        repeat(7) { index ->
                            val angle = Math.toRadians(-54.0 + index * 18.0)
                            val horizontal = forward.clone().setY(0.0)
                            if (horizontal.lengthSquared() < 1.0E-8) return@repeat
                            val point = player.location.clone().add(0.0, 1.05, 0.0)
                                .add(horizontal.normalize().rotateAroundY(angle).multiply(0.9))
                            particles.spawn(point, Particle.ELECTRIC_SPARK, count = 1)
                        }
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }

        override fun whenHit(context: DamageContext) {
            val now = player.world.fullTime
            if (now >= activeUntilTick) return
            val attacker = context.attacker.entity
            if (attacker.world != player.world) return
            val facing = player.eyeLocation.direction.setY(0.0)
            val incoming = attacker.boundingBox.center
                .subtract(player.boundingBox.center)
                .setY(0.0)
            if (facing.lengthSquared() < 1.0E-8 || incoming.lengthSquared() < 1.0E-8) return
            if (facing.normalize().dot(incoming.normalize()) < 0.0) return

            context.addDamageTakenMultiplier(0.4)
            particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.CRIT, count = 10, spread = 0.45)
            if (now != lastBlockSoundTick) {
                lastBlockSoundTick = now
                sounds.play(player, Sound.ITEM_SHIELD_BLOCK, volume = 0.8f, pitch = 1.15f)
            }
        }
    }
}
