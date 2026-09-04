package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val BARRIER_SHIELD_COOLDOWN_SECONDS = 30
private const val BARRIER_SHIELD_DURATION_TICKS = 80L
private const val BARRIER_DAMAGE_TAKEN_MULTIPLIER = 0.4

class Barrier : GameClass(), GameStatusHandler, GameEndHandler {
    override val classId = "barrier"
    override val name = "<gray>방벽"
    override val rank = Rank.C
    override val classItemMaterial = Material.SHIELD
    private val shieldSkill = RedSkill()
    override var skills: List<Skill> = listOf(shieldSkill)
    override var passives: List<BasePassive> = listOf()

    override fun onBattleStart() = shieldSkill.reset()
    override fun onGameTimePasses() = Unit
    override fun onGameEnd() = shieldSkill.reset()

    private inner class RedSkill : Skill(), WhenHitHandler {
        override val definitionId = "barrier/red-skill"
        override val name = "<bold>방패 세우기"
        override val description = listOf(
            "<gray>4초간 방패를 들어 바라보는 방향에서 <gold><bold>받는 피해를 60% 감소</bold><gray>시킨다."
        )
        override val cooldown = BARRIER_SHIELD_COOLDOWN_SECONDS

        private var activeUntilTick = 0L
        private var active = false
        private var activeDisplay: ItemDisplay? = null
        private var lastBlockSoundTick = Long.MIN_VALUE

        override fun use(): Boolean {
            reset()
            active = true
            activeUntilTick = game.combatTick + BARRIER_SHIELD_DURATION_TICKS
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
            activeDisplay = display
            sounds.play(player, Sound.ITEM_ARMOR_EQUIP_IRON, volume = 1.0f, pitch = 0.75f)

            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                override fun run() {
                    if (!active || activeDisplay !== display || !player.isOnline || player.isDead ||
                        game.combatTick >= activeUntilTick
                    ) {
                        if (activeDisplay === display) stopShield(playSound = player.isOnline)
                        else if (display.isValid) display.remove()
                        cancel()
                        return
                    }
                    val forward = player.eyeLocation.direction.normalize()
                    val displayLocation = player.location.clone().add(0.0, 1.15, 0.0)
                        .add(forward.clone().multiply(0.72))
                    displayLocation.yaw = player.location.yaw + 180.0f
                    displayLocation.pitch = 0.0f
                    display.teleport(displayLocation)

                    if (game.combatTick % 2 == 0L) {
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
            return true
        }

        override fun whenHit(context: DamageContext) {
            val now = game.combatTick
            if (!active || now >= activeUntilTick) {
                if (active) stopShield(playSound = player.isOnline)
                return
            }
            val attacker = context.attacker.entity
            if (attacker.world != player.world) return
            val facing = player.eyeLocation.direction.setY(0.0)
            val incoming = attacker.boundingBox.center
                .subtract(player.boundingBox.center)
                .setY(0.0)
            if (facing.lengthSquared() < 1.0E-8 || incoming.lengthSquared() < 1.0E-8) return
            if (facing.normalize().dot(incoming.normalize()) < 0.0) return

            context.addDamageTakenMultiplier(BARRIER_DAMAGE_TAKEN_MULTIPLIER)
            particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.CRIT, count = 10, spread = 0.45)
            if (now != lastBlockSoundTick) {
                lastBlockSoundTick = now
                sounds.play(player, Sound.ITEM_SHIELD_BLOCK, volume = 0.8f, pitch = 1.15f)
            }
        }

        fun reset() = stopShield(playSound = false)

        private fun stopShield(playSound: Boolean) {
            val wasActive = active
            active = false
            activeUntilTick = 0L
            lastBlockSoundTick = Long.MIN_VALUE
            activeDisplay?.let { if (it.isValid) it.remove() }
            activeDisplay = null
            if (playSound && wasActive) {
                sounds.play(player, Sound.ITEM_SHIELD_BLOCK, volume = 0.7f, pitch = 0.65f)
            }
        }
    }
}
