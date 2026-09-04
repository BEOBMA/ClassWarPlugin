package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import kotlin.math.cos

private const val SHY_VIEW_RANGE = 48.0

class ShyPerson : GameClass(), GameStatusHandler {
    override val classId = "shy-person"
    override val name = "<gray>부끄럼쟁이"
    override val rank = Rank.B
    override val classItemMaterial = Material.PUFFERFISH
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private val visibleObservers = mutableSetOf<java.util.UUID>()

    override fun onBattleStart() {
        visibleObservers.clear()
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    cancel()
                    return
                }
                revealObservers(tick++)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 3L))
    }

    override fun onGameTimePasses() = Unit

    private fun revealObservers(tick: Int) {
        val current = game.playerDatas.asSequence().filterIsInstance<PlayerData>()
            .filter { it != playerData && it.player.isOnline && !it.entityStatus.isDead && seesMe(it) }
            .toList()
        val currentIds = current.mapTo(mutableSetOf()) { it.uniqueId }
        if (currentIds.any { it !in visibleObservers }) {
            sounds.playTo(player, Sound.ENTITY_PUFFER_FISH_BLOW_UP, volume = 0.45f, pitch = 1.45f)
            particles.spawnTo(player, player.location.add(0.0, 1.2, 0.0), Particle.HEART, count = 3, spread = 0.35)
        }
        visibleObservers.clear()
        visibleObservers.addAll(currentIds)
        if (tick % 2 != 0) return
        current.forEach { observer ->
            val center = observer.entity.boundingBox.center.toLocation(player.world)
            particles.spawnTo(player, center, Particle.GLOW, count = 10, spread = 0.45, speed = 0.015)
            drawPrivateLine(player.eyeLocation, center)
        }
    }

    private fun seesMe(observer: PlayerData): Boolean {
        if (observer.player.world != player.world || !observer.player.hasLineOfSight(player)) return false
        val eye = observer.player.eyeLocation
        if (HitboxUtil.distanceSquared(player.boundingBox, eye.toVector()) > SHY_VIEW_RANGE * SHY_VIEW_RANGE) return false
        val point = HitboxUtil.closestPoint(player.boundingBox, eye.toVector())
        val vector = point.subtract(eye.toVector())
        if (vector.lengthSquared() < 1.0E-8) return true
        return eye.direction.normalize().dot(vector.normalize()) >= cos(Math.toRadians(42.0))
    }

    private fun drawPrivateLine(from: org.bukkit.Location, to: org.bukkit.Location) {
        val delta = to.toVector().subtract(from.toVector())
        val points = (delta.length() / 1.4).toInt().coerceAtLeast(1)
        val step = delta.multiply(1.0 / points)
        val cursor = from.clone()
        repeat(points) {
            particles.spawnTo(player, cursor, Particle.WITCH, 1, 0.03, 0.0)
            cursor.add(step)
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>부끄러움"
        override val description = listOf(
            "<gray>패시브", "", "<gray>다른 플레이어의 시야 범위에 있을 때",
            "<gray>자신은 부끄러움을 느끼며 대상의 위치를 알 수 있다."
        )
    }
}
