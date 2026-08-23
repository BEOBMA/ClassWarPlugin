package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.MovementInputHandler
import org.beobma.classWarPlugin.manager.UtilManager.isActuallyGrounded
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector

class HighJumper : GameClass(), GameStatusHandler, MovementInputHandler, EnvironmentalDamageHandler {
    override val name = "<gray>높이뛰기 선수"
    override val rank = Rank.C
    override val classItemMaterial = Material.RABBIT_FOOT
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var chargeTicks = 0
    private var lastJumpInput = false
    private var fallImmunity = false
    private var becameAirborne = false

    override fun onBattleStart() {
        chargeTicks = 0
        lastJumpInput = false
        fallImmunity = false
        becameAirborne = false
        playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    cancel()
                    return
                }
                if (fallImmunity) {
                    if (!player.isActuallyGrounded()) becameAirborne = true
                    else if (becameAirborne) {
                        fallImmunity = false
                        becameAirborne = false
                    }
                }
                if (player.isSneaking && player.isActuallyGrounded()) {
                    chargeTicks = (chargeTicks + 2).coerceAtMost(200)
                    if (chargeTicks >= 60 && chargeTicks % 10 == 0) {
                        particles.spawn(player.location.clone().add(0.0, 0.1, 0.0), Particle.CLOUD, count = 6, spread = 0.45, speed = 0.025)
                        if (chargeTicks % 20 == 0) sounds.play(player, Sound.BLOCK_NOTE_BLOCK_HAT, volume = 0.35f, pitch = 0.8f + chargeTicks / 250f)
                    }
                } else if (!fallImmunity) {
                    chargeTicks = 0
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }
    override fun onGameTimePasses() = Unit

    override fun onPlayerInput(event: PlayerInputEvent) {
        val jump = event.input.isJump
        if (jump && !lastJumpInput && player.isSneaking && player.isActuallyGrounded() && chargeTicks >= 60) {
            val normalizedCharge = ((chargeTicks - 60) / 140.0).coerceIn(0.0, 1.0)
            val horizontal = player.eyeLocation.direction.setY(0.0).let {
                if (it.lengthSquared() > 1.0E-6) it.normalize().multiply(0.28 + normalizedCharge * 0.18) else Vector()
            }
            player.velocity = horizontal.setY(1.05 + normalizedCharge * 0.95)
            player.fallDistance = 0f
            fallImmunity = true
            becameAirborne = false
            chargeTicks = 0
            particles.spawn(player, Particle.CLOUD, count = 30, spread = 0.55, speed = 0.16)
            sounds.play(player, Sound.ENTITY_BREEZE_JUMP, volume = 0.9f, pitch = 0.82f)
        }
        lastJumpInput = jump
    }

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL || !fallImmunity) return
        event.isCancelled = true
        player.fallDistance = 0f
        fallImmunity = false
        becameAirborne = false
        particles.spawn(player.location, Particle.CLOUD, count = 22, spread = 0.55, speed = 0.08)
        sounds.play(player, Sound.BLOCK_SLIME_BLOCK_FALL, volume = 0.75f, pitch = 1.15f)
    }

    private class Passive : BasePassive() {
        override val name = "<bold>도약"
        override val description = listOf(
            "<gray>패시브", "", "<gray>웅크린 상태를 3초 이상 지속하면 힘을 모은다.",
            "<gray>힘을 모은 뒤 웅크린 상태에서 점프하면 모은 힘에 비례하여 높이 점프한다.",
            "<gray>점프한 후 처음 착지할 때의 낙하 피해는 0이 된다."
        )
    }
}
