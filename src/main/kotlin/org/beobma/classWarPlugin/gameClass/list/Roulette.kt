package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import kotlin.random.Random
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val ROULETTE_JACKPOT_CHANCE = 0.02
private const val ROULETTE_JACKPOT_DAMAGE_MULTIPLIER = 2.5
private const val ROULETTE_HIGH_DAMAGE_MULTIPLIER = 1.5
private const val ROULETTE_LOW_DAMAGE_MULTIPLIER = 0.5
private const val ROULETTE_MIRACLE_DAMAGE = 0.0

class Roulette : GameClass() {
    override val name = "<gray>룰렛"
    override val rank = Rank.B
    override val classItemMaterial = Material.COMPASS
    override var skills: List<Skill> = listOf()
    override var passives: List<BasePassive> = listOf(Passive())

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>행운의 돌림판~"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>피격 시 피해량이 50% 감소하거나, 50% 증가한다.",
            "<gray>공격 시 피해량이 50% 증가하거나, 50% 감소한다.", "",
            "<gray>극히 낮은 확률로 피격 시 피해량이 0이 된다.",
            "<gray>극히 낮은 확률로 공격 시 피해량이 150% 증가한다."
        )

        override fun onHit(context: DamageContext) {
            val jackpot = Random.nextDouble() < ROULETTE_JACKPOT_CHANCE
            val multiplier = when {
                jackpot -> ROULETTE_JACKPOT_DAMAGE_MULTIPLIER
                Random.nextBoolean() -> ROULETTE_HIGH_DAMAGE_MULTIPLIER
                else -> ROULETTE_LOW_DAMAGE_MULTIPLIER
            }
            context.addDamageDealtMultiplier(multiplier)
            val color = when {
                jackpot -> Color.fromRGB(255, 215, 0)
                multiplier > 1.0 -> Color.LIME
                else -> Color.GRAY
            }
            particles.spawn(
                player.location.clone().add(0.0, 1.2, 0.0),
                Particle.DUST,
                Particle.DustOptions(color, if (jackpot) 1.8f else 1.1f),
                org.beobma.classWarPlugin.effect.ParticleOptions.spread(if (jackpot) 20 else 8, 0.4, 0.05),
            )
            sounds.playTo(
                player,
                if (jackpot) Sound.ENTITY_PLAYER_LEVELUP else Sound.BLOCK_NOTE_BLOCK_HAT,
                volume = if (jackpot) 0.9f else 0.45f,
                pitch = if (multiplier > 1.0) 1.65f else 0.75f,
            )
        }

        override fun whenHit(context: DamageContext) {
            val miracle = Random.nextDouble() < ROULETTE_JACKPOT_CHANCE
            if (miracle) context.capDamage(ROULETTE_MIRACLE_DAMAGE)
            else context.addDamageTakenMultiplier(
                if (Random.nextBoolean()) ROULETTE_LOW_DAMAGE_MULTIPLIER
                else ROULETTE_HIGH_DAMAGE_MULTIPLIER
            )

            particles.spawn(
                player,
                if (miracle) Particle.TOTEM_OF_UNDYING else Particle.ENCHANT,
                count = if (miracle) 26 else 9,
                spread = 0.55,
                speed = if (miracle) 0.12 else 0.04,
            )
            sounds.playTo(
                player,
                if (miracle) Sound.ITEM_TOTEM_USE else Sound.BLOCK_NOTE_BLOCK_HAT,
                volume = if (miracle) 0.85f else 0.4f,
                pitch = if (miracle) 1.35f else 0.85f,
            )
        }
    }
}
