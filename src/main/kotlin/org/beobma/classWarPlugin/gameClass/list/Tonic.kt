package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import kotlin.random.Random

class Tonic : GameClass(), GameStatusHandler {
    override val name = "<gray>보약"
    override val rank = Rank.C
    override val classItemMaterial = Material.OMINOUS_BOTTLE
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())
    private var uses = 0
    private var damageDealtMultiplier = 1.0
    private var damageTakenMultiplier = 1.0

    override fun onBattleStart() {
        uses = 0
        damageDealtMultiplier = 1.0
        damageTakenMultiplier = 1.0
        playerData.getOrCreateStatus(playerData) { TonicStackStatus() }.updatePower(0)
    }
    override fun onGameTimePasses() = Unit

    private inner class RedSkill : Skill() {
        override val name = "<bold>'보약' 사용"
        override val description = listOf("<gray>몸에 좋다.")
        override val cooldown = 10
        override fun use() {
            uses++
            damageDealtMultiplier = 1.0 + uses * 0.05
            playerData.getOrCreateStatus(playerData) { TonicStackStatus() }.updatePower(uses)
            if (uses >= 5) applyHiddenDebuff()
            player.sendMiniMessage("<green><bold>[보약]</bold> <gray>가하는 피해가 증가했습니다.")
            particles.spawn(player, Particle.HAPPY_VILLAGER, count = 24, spread = 0.55, speed = 0.08)
            particles.spawn(player, Particle.EFFECT, count = 12, spread = 0.4, speed = 0.05)
            sounds.play(player, Sound.ENTITY_GENERIC_DRINK, volume = 0.85f, pitch = 0.82f)
        }
    }

    private fun applyHiddenDebuff() {
        val escalation = (uses - 4).coerceAtLeast(1)
        val severity = (0.08 + escalation * 0.035).coerceAtMost(0.55)
        when (Random.nextInt(6)) {
            0 -> player.getAttribute(Attribute.MAX_HEALTH)?.let {
                val healthLoss = (1.5 + escalation * 0.65).coerceAtMost(9.0)
                it.baseValue = (it.baseValue - healthLoss).coerceAtLeast(1.0)
                player.health = player.health.coerceAtMost(it.value)
            }
            1 -> player.getAttribute(Attribute.MOVEMENT_SPEED)?.let {
                it.baseValue = (it.baseValue * (1.0 - severity)).coerceAtLeast(0.012)
            }
            2 -> player.getAttribute(Attribute.ATTACK_SPEED)?.let {
                it.baseValue = (it.baseValue * (1.0 - severity * 0.9)).coerceAtLeast(0.25)
            }
            3 -> player.getAttribute(Attribute.JUMP_STRENGTH)?.let {
                it.baseValue = (it.baseValue * (1.0 - severity)).coerceAtLeast(0.05)
            }
            4 -> player.getAttribute(Attribute.SCALE)?.let {
                it.baseValue = (it.baseValue * (1.0 - severity * 0.65)).coerceAtLeast(0.25)
            }
            else -> damageTakenMultiplier = (damageTakenMultiplier * (1.0 + severity * 1.6)).coerceAtMost(8.0)
        }
        particles.spawn(player, Particle.WITCH, count = (12 + escalation * 2).coerceAtMost(42), spread = 0.45, speed = 0.055)
        sounds.play(player, Sound.BLOCK_BREWING_STAND_BREW, volume = 0.35f, pitch = (1.15 - severity).toFloat())
    }

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>약효"
        override val description = listOf("<gray>마신 보약 하나당 가하는 피해가 5% 증가한다.")
        override fun onHit(context: DamageContext) = context.addDamageDealtMultiplier(damageDealtMultiplier)
        override fun whenHit(context: DamageContext) = context.addDamageTakenMultiplier(damageTakenMultiplier)
    }
}

private class TonicStackStatus : StatusAbnormality() {
    override val name = "<green><bold>보약</bold><gray>"
    override val description = listOf("<gray>마신 보약의 수이다. 1개마다 가하는 피해가 5% 증가한다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = null
    override val showMaxPower = false
    override var duration: Int? = null
}
