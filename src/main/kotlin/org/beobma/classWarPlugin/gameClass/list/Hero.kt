package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import kotlin.math.ceil
import kotlin.random.Random

class Hero : GameClass(), GameStatusHandler, EnvironmentalDamageHandler {
    override val classId = "hero"
    override val name = "<gray>영웅"
    override val rank = Rank.S
    override val classItemMaterial = Material.TOTEM_OF_UNDYING
    override var skills: List<Skill> = emptyList()
    override var passives: List<Passive> = listOf(Indomitable())
    private var survivalChance = 100
    private var thresholdRolled = false

    override fun onBattleStart() {
        survivalChance = 100
        thresholdRolled = false
    }
    override fun onGameTimePasses() {}

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event is EntityDamageByEntityEvent) return
        if (player.health <= 0.0 || playerStatus.isDead) return
        if (player.health - event.finalDamage <= 0.0 && tryResistDeath()) event.isCancelled = true
    }

    private fun tryResistDeath(): Boolean {
        if (survivalChance <= 0 || Random.nextDouble() >= org.beobma.classWarPlugin.growth.GrowthScaling.chance(playerData, survivalChance / 100.0)) return false
        survivalChance = (survivalChance - 20).coerceAtLeast(0)
        player.health = 1.0
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 85, spread = 0.9, speed = 0.2)
        sounds.play(player, Sound.ITEM_TOTEM_USE, volume = 1.0f, pitch = 1.12f)
        return true
    }


    private inner class Indomitable : Passive(), WhenHitHandler {
        override val name = "<bold>불굴"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>사망 시 {g:chance:100}% 확률로 사망하지 않는다.",
            "<gray>위 효과가 발동할 때마다 확률은 20%p씩 감소한다."
        )
        override fun whenHit(context: DamageContext) {
            if (player.health - context.damage <= 0.0 && tryResistDeath()) context.isCancelled = true
        }
    }
}
