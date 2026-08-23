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
    override val name = "<gray>영웅"
    override val rank = Rank.S
    override val classItemMaterial = Material.TOTEM_OF_UNDYING
    override var skills: List<Skill> = emptyList()
    override var passives: List<Passive> = listOf(Indomitable(), Resolve())
    private var survivalChance = 80
    private var stress = 0
    private var thresholdRolled = false

    override fun onBattleStart() {
        survivalChance = 80
        stress = 0
        thresholdRolled = false
        updateStressStatus()
    }
    override fun onGameTimePasses() {
        if (stress > 0) {
            stress--
            if (stress < 100) thresholdRolled = false
            updateStressStatus()
        }
    }
    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event is EntityDamageByEntityEvent) return
        if (addStress(ceil(event.finalDamage * 5.0).toInt().coerceAtLeast(1))) {
            event.isCancelled = true
            return
        }
        if (player.health <= 0.0 || playerStatus.isDead) return
        if (player.health - event.finalDamage <= 0.0 && tryResistDeath()) event.isCancelled = true
    }

    private fun tryResistDeath(): Boolean {
        if (survivalChance <= 0 || Random.nextInt(100) >= survivalChance) return false
        survivalChance = (survivalChance - 20).coerceAtLeast(0)
        player.health = 1.0
        addStress(25)
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 85, spread = 0.9, speed = 0.2)
        sounds.play(player, Sound.ITEM_TOTEM_USE, volume = 1.0f, pitch = 1.12f)
        return true
    }

    private fun addStress(amount: Int): Boolean {
        if (playerStatus.isDead) return false
        stress = (stress + amount).coerceAtMost(200)
        if (stress >= 200) {
            updateStressStatus()
            particles.spawn(player, Particle.SOUL_FIRE_FLAME, count = 70, spread = 0.8, speed = 0.15)
            sounds.play(player, Sound.ENTITY_WITHER_DEATH, volume = 0.8f, pitch = 0.55f)
            player.health = 0.0
            return true
        }
        if (stress >= 100 && !thresholdRolled) {
            thresholdRolled = true
            if (Random.nextBoolean()) {
                stress = 0
                thresholdRolled = false
                playerData.heal(6.0, DamageType.Normal, playerData)
                particles.spawn(player, Particle.END_ROD, count = 48, spread = 0.75, speed = 0.12)
                sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, volume = 0.8f, pitch = 1.2f)
            }
        }
        updateStressStatus()
        return false
    }

    private fun updateStressStatus() {
        playerData.getOrCreateStatus(playerData) { HeroStressStatus() }.updatePower(stress)
    }

    private inner class Indomitable : Passive(), WhenHitHandler {
        override val name = "<bold>불굴"
        override val description = listOf(
            "<gray>패시브", "", "<gray>사망 시 80% 확률로 사망하지 않는다.", "<gray>위 효과가 발동할 때마다 확률은 20%씩 감소한다."
        )
        override fun whenHit(context: DamageContext) {
            if (player.health - context.damage <= 0.0 && tryResistDeath()) context.isCancelled = true
        }
    }

    private inner class Resolve : Passive(), WhenHitHandler {
        override val name = "<bold>결의"
        override val description = listOf(
            "<gray>패시브", "", "<gray>자신은 스트레스 수치를 가진다.",
            "<gray>피해를 받거나 불굴 효과로 죽음에 저항할 때마다 수치가 증가한다.",
            "<gray>수치가 100에 도달하면 50% 확률로 영웅의 기상이 발동한다.",
            "<gray>영웅의 기상 발동 시 스트레스 수치가 0이 되고, 체력을 6 회복한다.",
            "<gray>스트레스 수치는 매 초마다 1씩 감소하고, 200이 되면 자신은 {keyword:Execution}된다."
        )
        override fun whenHit(context: DamageContext) {
            if (addStress(ceil(context.damage * 5.0).toInt().coerceAtLeast(1))) context.isCancelled = true
        }
    }
}

private class HeroStressStatus : StatusAbnormality() {
    override val name = "<red><bold>스트레스</bold><gray>"
    override val description = listOf("<gray>영웅의 현재 스트레스 수치이다.")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = 200
    override var duration: Int? = null
}
