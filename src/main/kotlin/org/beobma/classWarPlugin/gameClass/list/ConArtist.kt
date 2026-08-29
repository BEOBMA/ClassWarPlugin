package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.scheduler.BukkitRunnable

class ConArtist : GameClass(), GameStatusHandler, EnvironmentalDamageHandler {
    override val name = "<gray>사기꾼"
    override val rank = Rank.B
    override val classItemMaterial = Material.ARMOR_STAND
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var fakingDeath = false
    private var fakeDeathUsed = false

    override fun onBattleStart() {
        fakingDeath = false
        fakeDeathUsed = false
    }
    override fun onGameTimePasses() = Unit

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (player.health - event.finalDamage <= 0.0 && fakeDeath()) event.isCancelled = true
    }

    private fun fakeDeath(): Boolean {
        if (fakingDeath || fakeDeathUsed || playerStatus.isDead) return false
        fakeDeathUsed = true
        fakingDeath = true
        player.health = 1.0
        val stealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also {
            it.applyStatus(duration = 20, powerSet = 1)
        }
        playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (stealth.power > 0) stealth.remove()
                fakingDeath = false
            }
        }.runTaskLater(ClassWarPlugin.instance, 400L))
        return true
    }

    private inner class Passive : BasePassive(), WhenHitHandler {
        override val name = "<bold>가짜 죽음"
        override val description = listOf(
            "<gray>패시브", "", "<gray>게임당 1회, 사망 시 사망을 {keyword:Invalidity}로 하고, 사망한 것처럼 위장하며 20초간 {keyword:Stealth} 상태가 된다."
        )
        override fun whenHit(context: DamageContext) {
            if (player.health - context.damage <= 0.0 && fakeDeath()) context.isCancelled = true
        }
    }
}
