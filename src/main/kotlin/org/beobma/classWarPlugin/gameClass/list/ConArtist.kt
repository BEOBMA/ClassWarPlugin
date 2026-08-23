package org.beobma.classWarPlugin.gameClass.list

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
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
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.scheduler.BukkitRunnable

class ConArtist : GameClass(), GameStatusHandler, EnvironmentalDamageHandler {
    override val name = "<gray>사기꾼"
    override val rank = Rank.B
    override val classItemMaterial = Material.ARMOR_STAND
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var fakingDeath = false

    override fun onBattleStart() { fakingDeath = false }
    override fun onGameTimePasses() = Unit

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (player.health - event.finalDamage <= 0.0 && fakeDeath()) event.isCancelled = true
    }

    private fun fakeDeath(): Boolean {
        if (fakingDeath || playerStatus.isDead) return false
        fakingDeath = true
        player.health = 1.0
        val stealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also {
            it.applyStatus(duration = 20, powerSet = 1)
        }
        val message = MiniMessage.miniMessage().deserialize("<red><bold>[탈락]</bold> <white>${player.name}<gray>님이 사망했습니다.")
        game.playerDatas.filterIsInstance<PlayerData>().filter { it.player.isOnline }.forEach { it.player.sendMessage(message) }
        particles.spawn(player, Particle.POOF, count = 65, spread = 0.75, speed = 0.12)
        particles.spawn(player, Particle.LARGE_SMOKE, count = 38, spread = 0.65, speed = 0.07)
        sounds.play(player.location, Sound.ENTITY_PLAYER_DEATH, volume = 0.9f, pitch = 0.9f)
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
            "<gray>패시브", "", "<gray>사망 시 {keyword:Invalidity}로 하나, 사망한 것처럼 위장하고 자신은 20초간 {keyword:Stealth} 상태가 된다."
        )
        override fun whenHit(context: DamageContext) {
            if (player.health - context.damage <= 0.0 && fakeDeath()) context.isCancelled = true
        }
    }
}
