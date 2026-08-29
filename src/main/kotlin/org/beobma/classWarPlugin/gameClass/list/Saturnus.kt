package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Saturnus : PlanetClass(), GameStatusHandler, GameEndHandler {
    override val name = "<gray>토성"
    override val rank = Rank.A
    override val classItemMaterial = Material.SANDSTONE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private val rocks = MutableList(7) { Rock(it * 2.0 * PI / 7.0) }
    private var orbitRadius = 5.0

    internal fun useSolarSystemOrbit() {
        orbitRadius = 10.5
    }

    override fun onBattleStart() {
        rocks.forEach {
            it.display?.remove()
            it.display = null
            it.respawnAt = 0L
            it.collisionEnabledAt = 0L
        }
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    clearDisplays()
                    cancel()
                    return
                }
                if (!isPowerEnabled() || game.isPaused) {
                    clearDisplays()
                    return
                }
                val now = Bukkit.getCurrentTick().toLong()
                if (tick % 10 == 0) {
                    particles.circle(player.location.clone().add(0.0, 1.0, 0.0), Particle.CRIT, orbitRadius, 56)
                }
                rocks.forEachIndexed { index, rock ->
                    if (rock.respawnAt > now) return@forEachIndexed
                    val angle = rock.baseAngle + tick * 0.018
                    val location = player.location.clone().add(
                        cos(angle) * orbitRadius,
                        1.0 + sin(angle * 2.0 + index) * 0.18,
                        sin(angle) * orbitRadius,
                    ).apply { yaw = 0.0f; pitch = 0.0f }
                    val display = rock.display?.takeIf { it.isValid } ?: spawnRock(location).also {
                        rock.display = it
                        if (rock.respawnAt > 0L) {
                            rock.respawnAt = 0L
                            rock.collisionEnabledAt = now + 10L
                            particles.spawn(location, Particle.BLOCK, Material.STONE.createBlockData(),
                                org.beobma.classWarPlugin.effect.ParticleOptions.spread(12, 0.25, 0.04))
                            sounds.play(location, Sound.BLOCK_STONE_PLACE, volume = 0.45f, pitch = 1.25f)
                        }
                    }
                    display.teleport(location)
                    if (now < rock.collisionEnabledAt) return@forEachIndexed
                    val target = playerData.radius(location, TargetType.Enemy, 0.8, false)
                        .firstOrNull { org.beobma.classWarPlugin.util.HitboxUtil.intersectsSphere(it.entity.boundingBox, location.toVector(), 0.72) }
                        ?: return@forEachIndexed
                    target.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                    display.remove()
                    rock.display = null
                    rock.respawnAt = now + 200L
                    particles.spawn(location, Particle.BLOCK, Material.STONE.createBlockData(),
                        org.beobma.classWarPlugin.effect.ParticleOptions.spread(18, 0.35, 0.08))
                    sounds.play(location, Sound.BLOCK_STONE_BREAK, volume = 0.6f, pitch = 0.82f)
                }
                tick += 2
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    override fun onGameTimePasses() = Unit
    override fun onGameEnd() = clearDisplays()

    private fun spawnRock(location: org.bukkit.Location): BlockDisplay =
        location.world.spawn(location, BlockDisplay::class.java).apply {
            block = Material.STONE.createBlockData()
            transformation = Transformation(
                Vector3f(-0.3f, -0.3f, -0.3f), Quaternionf(),
                Vector3f(0.6f, 0.6f, 0.6f), Quaternionf(),
            )
            brightness = org.bukkit.entity.Display.Brightness(12, 12)
            isPersistent = false
            TemporaryDisplayManager.mark(this, player.uniqueId)
        }

    private fun clearDisplays() {
        rocks.forEach { it.display?.remove(); it.display = null }
    }

    private data class Rock(
        val baseAngle: Double,
        var display: BlockDisplay? = null,
        var respawnAt: Long = 0L,
        var collisionEnabledAt: Long = 0L,
    )

    private class Passive : BasePassive() {
        override val name = "<bold>토성"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>자신 주위 반지름 5칸 크기로 원형 고리가 생긴다.",
            "<gray>원형 고리에 바위가 7개 생성되며, 자신 주위를 일정한 간격으로 공전한다.",
            "<gray>바위에 닿은 적에게 2의 피해를 입히고, 바위는 파괴된다.",
            "<gray>바위는 10초 후 재생된다."
        )
    }
}
