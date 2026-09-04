package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Bleeding
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.random.Random

private const val LUCKY_DODGE_CHANCE = 0.25
private const val LUCKY_FALL_SAVE_CHANCE = 0.35
private const val LUCKY_ATTACK_PROC_CHANCE = 0.35

class LuckyOne : GameClass(), EnvironmentalDamageHandler, OnHitHandler, WhenHitHandler {
    override val classId = "lucky-one"
    override val name = "<gray>행운아"
    override val rank = Rank.A
    override val classItemMaterial = Material.GOLD_BLOCK
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL || Random.nextDouble() >= LUCKY_FALL_SAVE_CHANCE) return
        event.isCancelled = true
        player.sendMiniMessage("<gold><bold>[행운] <white>낙법에 성공했다.")
        particles.spawn(player.location, Particle.CLOUD, count = 18, spread = 0.55, speed = 0.08)
        sounds.play(player, Sound.ENTITY_PLAYER_SMALL_FALL, volume = 0.75f, pitch = 1.65f)
    }

    override fun whenHit(context: DamageContext) {
        if (context.attacker == playerData || Random.nextDouble() >= LUCKY_DODGE_CHANCE) return
        context.isCancelled = true
        val message = if (Random.nextBoolean()) "적의 공격을 회피했다." else "적의 공격이 빗나갔다."
        player.sendMiniMessage("<gold><bold>[행운] <white>$message")
        context.attacker.player.sendMiniMessage("<yellow>[!] ${player.name}님이 공격을 피했습니다.")
        particles.spawn(player, Particle.POOF, count = 18, spread = 0.55, speed = 0.08)
        sounds.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.45f, pitch = 1.8f)
    }

    override fun onHit(context: DamageContext) {
        if (Random.nextDouble() >= LUCKY_ATTACK_PROC_CHANCE) return
        when (Random.nextInt(3)) {
            0 -> {
                context.target.addStatus(MoveSpeedDecrease(), playerData)
                    .applyStatus(duration = 3, powerSet = 35)
                notifyTarget(context.target as? PlayerData, "광대뼈를 맞았다.")
                particles.spawn(context.target.entity, Particle.CRIT, count = 12, spread = 0.35, speed = 0.08)
            }
            1 -> {
                context.addDamageDealtMultiplier(1.5)
                notifyTarget(context.target as? PlayerData, "그냥 아프다.")
                particles.spawn(context.target.entity, Particle.DAMAGE_INDICATOR, count = 8, spread = 0.3, speed = 0.05)
            }
            else -> {
                context.target.addStatus(Bleeding(), playerData)
                    .applyStatus(duration = 6, powerDelta = 2)
                notifyTarget(context.target as? PlayerData, "멍이 들었다.")
                particles.spawn(context.target.entity.boundingBox.center.toLocation(context.target.entity.world), Particle.FALLING_DUST, Material.REDSTONE_BLOCK.createBlockData(),
                    org.beobma.classWarPlugin.effect.ParticleOptions.spread(10, 0.35, 0.02))
            }
        }
        sounds.play(context.target.entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.7f, pitch = 1.45f)
    }

    private fun notifyTarget(target: PlayerData?, message: String) {
        player.sendMiniMessage("<gold><bold>[행운] <white>$message")
        target?.player?.sendMiniMessage("<red><bold>[불운] <white>$message")
    }

    private class Passive : BasePassive() {
        override val name = "<bold>행운"
        override val description = listOf("<gray>패시브", "", "<gray>모든 상황에서 자신에게 행운이 따른다.")
    }
}
