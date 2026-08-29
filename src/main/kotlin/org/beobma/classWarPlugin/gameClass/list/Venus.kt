package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Enchantment
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class Venus : PlanetClass(), GameStatusHandler {
    override val name = "<gray>금성"
    override val rank = Rank.A
    override val classItemMaterial = Material.BLACK_CONCRETE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private val cooldownUntil = mutableMapOf<UUID, Long>()

    override fun onBattleStart() {
        cooldownUntil.clear()
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    cancel()
                    return
                }
                if (!isPowerEnabled() || game.isPaused) return
                val now = player.world.fullTime
                cooldownUntil.entries.removeIf { it.value <= now }
                playerData.radius(player.location, TargetType.Enemy, 5.0, false)
                    .filter { (cooldownUntil[it.entity.uniqueId] ?: 0L) <= now }
                    .forEach { target ->
                        cooldownUntil[target.entity.uniqueId] = now + 400L
                        target.getOrCreateStatus(playerData) { Enchantment() }
                            .applyStatus(duration = 3, powerSet = 1)
                        particles.spawn(target.entity, Particle.HEART, count = 18, spread = 0.55, speed = 0.06)
                        particles.spawn(target.entity, Particle.WITCH, count = 12, spread = 0.48, speed = 0.035)
                        sounds.play(target.entity, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, volume = 0.55f, pitch = 1.65f)
                    }
                if (tick++ % 10 == 0) particles.circle(player.location.clone().add(0.0, 0.2, 0.0), Particle.WITCH, 5.0, 28)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 4L))
    }

    override fun onGameTimePasses() = Unit

    private class Passive : BasePassive() {
        override val name = "<bold>금성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>자신 주위 5칸 이내에 접근한 적을 3초간 {keyword:Enchantment} 상태로 만든다.",
            "<gray>이 효과는 대상 당 20초의 재사용 대기 시간을 가진다.",
        )
    }
}
