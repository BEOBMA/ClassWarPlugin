package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getTargetCandidates
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class Jupiter : PlanetClass(), GameStatusHandler {
    override val name = "<gray>목성"
    override val rank = Rank.B
    override val classItemMaterial = Material.SCULK
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private val clouds = ArrayDeque<GasCloud>()
    private val lastDamageTick = mutableMapOf<UUID, Long>()

    override fun onBattleStart() {
        clouds.clear()
        lastDamageTick.clear()
        var lastLocation = player.location.clone()
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    clouds.clear()
                    cancel()
                    return
                }
                if (!isPowerEnabled() || game.isPaused) {
                    clouds.clear()
                    lastLocation = player.location.clone()
                    return
                }
                val now = Bukkit.getCurrentTick().toLong()
                clouds.removeIf { it.expiresAt <= now || it.location.world != player.world }
                val current = player.location
                val moved = current.world == lastLocation.world && current.distanceSquared(lastLocation) >= 0.09
                if (moved && tick % 2 == 0) {
                    clouds.addLast(GasCloud(player.boundingBox.center.toLocation(player.world), now + 50L))
                    while (clouds.size > 16) clouds.removeFirst()
                    lastLocation = current.clone()
                }

                val dust = Particle.DustOptions(Color.fromRGB(210, 236, 245), 1.6f)
                clouds.forEachIndexed { index, cloud ->
                    if ((tick + index) % 3 == 0) {
                        particles.spawn(cloud.location, Particle.DUST, dust,
                            org.beobma.classWarPlugin.effect.ParticleOptions.spread(5, 0.7, 0.008))
                        particles.spawn(cloud.location, Particle.CLOUD, count = 2, spread = 0.65, speed = 0.008)
                    }
                }

                val training = PlayerTagManager.isTraining(player)
                playerData.getTargetCandidates().filter { target ->
                    target != playerData && !target.entityStatus.isDead && target.entity.isValid &&
                        target.entity.world == player.world &&
                        ((target is PlayerData && playerData.isEnemyOf(target)) || (target !is PlayerData && training)) &&
                        clouds.any { HitboxUtil.intersectsSphere(target.entity.boundingBox, it.location.toVector(), 1.25) }
                }.forEach { target ->
                    val previousDamageTick = lastDamageTick[target.entity.uniqueId]
                    if (previousDamageTick != null && now - previousDamageTick < 20L) return@forEach
                    lastDamageTick[target.entity.uniqueId] = now
                    target.damage(2.0, DamageType.StatusAbnormality, playerData, true, damagePath = DamagePath.STATUS_EFFECT)
                    particles.spawn(target.entity, Particle.CLOUD, count = 12, spread = 0.45, speed = 0.04)
                    sounds.play(target.entity, Sound.BLOCK_FIRE_EXTINGUISH, volume = 0.3f, pitch = 0.65f)
                }
                tick += 2
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    override fun onGameTimePasses() = Unit

    private data class GasCloud(val location: Location, val expiresAt: Long)

    private class Passive : BasePassive() {
        override val name = "<bold>목성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>이동 시 자신의 위치에 2.5초간 지속되는 수소를 내뿜는다.",
            "<gray>수소에 닿은 적에게 초당 2의 피해를 입힌다."
        )
    }
}
