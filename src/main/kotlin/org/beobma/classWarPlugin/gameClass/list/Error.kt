package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.AttributeEffects

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import kotlin.random.Random

class Error : GameClass(), GameStatusHandler, GameEndHandler {
    override val classId = "error"
    override val name = "<obfuscated>AIJ9wjfjo2</obfuscated>"
    override val rank = Rank.A
    override val classItemMaterial = Material.GRAY_STAINED_GLASS_PANE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var seconds = 0
    private var dealtMultiplier = 1.0
    private var takenMultiplier = 1.0
    private val effects = mutableMapOf<Attribute, AttributeEffects.Lease>()

    override fun onBattleStart() {
        seconds = 0
        dealtMultiplier = 1.0
        takenMultiplier = 1.0
        effects.values.forEach { it.close() }
        effects.clear()
        listOf(Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH, Attribute.SCALE).forEach { attribute ->
            effects[attribute] = playerData.attributeEffects.multiply(abilityScope, attribute, 1.0)
        }
    }
    override fun onGameTimePasses() {
        if (++seconds % 10 != 0 || playerStatus.isDead) return
        randomizeStats()
    }
    override fun onGameEnd() {
        effects.values.forEach { it.close() }
        effects.clear()
    }

    private fun randomizeStats() {
        dealtMultiplier = Random.nextDouble(0.25, 2.5)
        takenMultiplier = Random.nextDouble(0.25, 2.5)
        fun factor(min: Double, max: Double) = Random.nextDouble(min, max)
        effects[Attribute.MAX_HEALTH]?.setMultiplier(factor(0.3, 2.2))
        effects[Attribute.MOVEMENT_SPEED]?.setMultiplier(factor(0.35, 2.0))
        effects[Attribute.JUMP_STRENGTH]?.setMultiplier(factor(0.4, 2.1))
        effects[Attribute.SCALE]?.setMultiplier(factor(0.35, 1.9))
        particles.spawn(player, Particle.WITCH, count = 55, spread = 0.9, speed = 0.16)
        particles.spawn(player, Particle.ELECTRIC_SPARK, count = 28, spread = 0.75, speed = 0.12)
        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume = 0.65f, pitch = Random.nextDouble(0.5, 1.8).toFloat())
    }

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<obfuscated>AIJ9wjfjo2"
        override val description = listOf(
            "<gray>패시브", "", "<gray>10초마다 자신의 모든 능력치가 무작위로 조정된다.",
            "<gray>조정되는 능력치는 아래와 같다.", "", "<gray>  - 가하는 피해량", "<gray>  - 받는 피해량",
            "<gray>  - 최대 체력", "<gray>  - 이동 속도", "<gray>  - 점프 높이", "<gray>  - 크기"
        )
        override fun onHit(context: DamageContext) = context.addDamageDealtMultiplier(dealtMultiplier)
        override fun whenHit(context: DamageContext) = context.addDamageTakenMultiplier(takenMultiplier)
    }
}
