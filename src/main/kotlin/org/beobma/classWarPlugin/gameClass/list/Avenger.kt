package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.AttackSpeedIncrease
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class Avenger : GameClass(), GameStatusHandler, EnvironmentalDamageHandler {
    override val name = "<gray>복수자"
    override val rank = Rank.A
    override val classItemMaterial = Material.IRON_SPEAR
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private var activated = false
    private var respiteActive = false
    private var revengeTarget: UUID? = null

    override fun onBattleStart() {
        activated = false
        respiteActive = false
        revengeTarget = null
    }
    override fun onGameTimePasses() = Unit

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (player.health - event.finalDamage > 0.0) return
        val killerId = player.killer?.uniqueId ?: return
        if (activateRevenge(killerId)) event.isCancelled = true
    }

    private fun activateRevenge(killerId: UUID): Boolean {
        if (activated || playerStatus.isDead) return false
        activated = true
        respiteActive = true
        revengeTarget = killerId
        val maximum = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = 10.0.coerceAtMost(maximum).coerceAtLeast(1.0)
        playerData.addStatus(MoveSpeedIncrease(), playerData).applyStatus(duration = 8, powerSet = 50)
        playerData.addStatus(AttackSpeedIncrease(), playerData).applyStatus(duration = 8, powerSet = 50)
        player.sendMiniMessage("<dark_red><bold>[복수]</bold> <gray>8초 안에 자신을 쓰러뜨린 적에게 복수하십시오.")
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 75, spread = 0.85, speed = 0.18)
        sounds.play(player, Sound.ITEM_TOTEM_USE, volume = 0.9f, pitch = 0.72f)
        playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (!respiteActive || playerStatus.isDead) return
                respiteActive = false
                player.health = 0.0
            }
        }.runTaskLater(ClassWarPlugin.instance, 160L))
        return true
    }

    private inner class Passive : BasePassive(), OnHitHandler, WhenHitHandler {
        override val name = "<bold>복수"
        override val description = listOf(
            "<gray>패시브", "", "<gray>사망 시 사망을 {keyword:Invalidity}로 하고, 8초간 10의 {keyword:RespiteHealth}을 얻는다.",
            "<gray>이 효과가 발동하는 동안 자신의 <gold><bold>이동 속도와 공격 속도가 50% 증가</bold><gray>한다.",
            "<gray>단, 자신을 죽인 플레이어에게만 피해를 입힐 수 있다.", "<gray>이 효과는 1번만 발동할 수 있다."
        )
        override fun onHit(context: DamageContext) {
            if (respiteActive && context.target.entity.uniqueId != revengeTarget) context.isCancelled = true
        }
        override fun whenHit(context: DamageContext) {
            if (!activated && player.health - context.damage <= 0.0 && activateRevenge(context.attacker.uniqueId)) {
                context.isCancelled = true
            }
        }
    }
}
