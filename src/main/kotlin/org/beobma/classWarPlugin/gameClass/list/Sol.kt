package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Burn
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import java.util.UUID

class Sol : PlanetClass(), GameStatusHandler {
    override val classId = "sol"
    override val name = "<gray>태양"
    override val rank = Rank.B
    override val classItemMaterial = Material.MAGMA_BLOCK
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private val lastIgniteSound = mutableMapOf<UUID, Long>()

    override fun onBattleStart() {
        lastIgniteSound.clear()
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    cancel()
                    return
                }
                if (!isPowerEnabled() || game.isPaused) return
                val day = player.world.time in 0L..12300L
                val duration = if (day) 2 else 1
                if (tick % 5 == 0) {
                    particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.SMALL_FLAME,
                        count = if (day) 10 else 6, spread = 0.72, speed = 0.025)
                }
                playerData.radius(player.location, TargetType.Enemy, 5.0, false, hitAttackableObjects = true).forEach { target ->
                    target.getOrCreateStatus(playerData) { Burn() }
                        .applyStatus(duration = duration, powerSet = 1)
                    val now = game.combatTick
                    val lastSoundTick = lastIgniteSound[target.entity.uniqueId]
                    if (lastSoundTick == null || now - lastSoundTick >= 20L) {
                        lastIgniteSound[target.entity.uniqueId] = now
                        sounds.play(target.entity, Sound.ITEM_FIRECHARGE_USE, volume = 0.32f, pitch = if (day) 1.35f else 1.6f)
                    }
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    override fun onGameTimePasses() = Unit

    private class Passive : BasePassive() {
        override val name = "<bold>태양"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>자신 주위 5칸 이내에 접근한 적을 1초간 {keyword:Burn} 상태로 만든다.",
            "<gray>낮에는 효과가 강화되어 대신 2초간 {keyword:Burn} 상태로 만든다."
        )
    }
}
