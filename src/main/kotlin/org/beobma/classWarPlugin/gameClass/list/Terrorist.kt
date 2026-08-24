package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Terrorist : GameClass(), PlayerDeathHandler, GameEndHandler {
    override val name = "<gray>테러리스트"
    override val rank = Rank.C
    override val classItemMaterial = Material.TNT
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())
    private data class VisualBomb(
        val display: BlockDisplay,
        val location: org.bukkit.Location,
        val velocity: Vector,
        var rotation: Float,
    )
    private val bombDisplays = mutableSetOf<BlockDisplay>()
    private var bombTask: BukkitTask? = null

    override fun onPlayerDeath() {
        val origin = player.location.clone().add(0.0, 0.35, 0.0)
        val bombs = MutableList(30) { index ->
            val angle = index * (2.0 * PI / 30.0) + Random.nextDouble(-0.12, 0.12)
            val display = player.world.spawn(origin, BlockDisplay::class.java).apply {
                block = Material.TNT.createBlockData()
                billboard = Display.Billboard.FIXED
                brightness = Display.Brightness(15, 15)
                teleportDuration = 1
                isPersistent = false
                transformation = bombTransformation(index * 0.21f)
            }
            TemporaryDisplayManager.mark(display, player.uniqueId)
            bombDisplays += display
            VisualBomb(
                display,
                origin.clone(),
                Vector(cos(angle) * Random.nextDouble(0.22, 0.68), Random.nextDouble(0.25, 0.72), sin(angle) * Random.nextDouble(0.22, 0.68)),
                index * 0.21f,
            )
        }
        sounds.play(origin, Sound.ENTITY_TNT_PRIMED, volume = 1.25f, pitch = 0.72f)
        particles.spawn(origin, Particle.LARGE_SMOKE, count = 35, spread = 1.1, speed = 0.09)
        pendingUntil[game] = org.bukkit.Bukkit.getCurrentTick().toLong() + 60L

        bombTask = object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (tick < 60) {
                    bombs.forEach { bomb ->
                        if (!bomb.display.isValid) return@forEach
                        val next = bomb.location.clone().add(bomb.velocity)
                        if (next.block.type.isSolid) {
                            bomb.velocity.x *= 0.62
                            bomb.velocity.z *= 0.62
                            bomb.velocity.y = kotlin.math.abs(bomb.velocity.y) * 0.32
                        } else {
                            bomb.location.add(bomb.velocity)
                            bomb.velocity.y -= 0.045
                        }
                        bomb.rotation += 0.16f
                        bomb.display.transformation = bombTransformation(bomb.rotation)
                        bomb.display.teleport(bomb.location)
                        if (tick % 5 == 0) particles.spawn(bomb.location, Particle.SMOKE, count = 1, spread = 0.04, speed = 0.01)
                    }
                    tick++
                    return
                }
                bombs.forEach { bomb ->
                    val center = bomb.location.clone()
                    bomb.display.remove()
                    bombDisplays.remove(bomb.display)
                    playerData.radius(center, TargetType.Enemy, 3.0, false).filterIsInstance<PlayerData>().forEach { target ->
                        target.damage(5.0, DamageType.Normal, playerData, damagePath = DamagePath.STATUS_EFFECT)
                    }
                    particles.spawn(center, Particle.EXPLOSION, count = 2, spread = 0.25)
                    particles.spawn(center, Particle.FLAME, count = 14, spread = 0.8, speed = 0.12)
                }
                pendingUntil.remove(game)
                sounds.play(origin, Sound.ENTITY_GENERIC_EXPLODE, volume = 1.45f, pitch = 0.8f)
                bombTask = null
                cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        val task = bombTask ?: return
        game.tasks.add(task)
    }

    override fun onGameEnd() {
        bombTask?.cancel()
        bombTask = null
        bombDisplays.toList().forEach(BlockDisplay::remove)
        bombDisplays.clear()
        clearPending(game)
    }

    private fun bombTransformation(angle: Float): Transformation {
        val rotation = Quaternionf().rotateXYZ(angle * 0.7f, angle, angle * 0.45f)
        val scale = Vector3f(0.64f, 0.64f, 0.64f)
        val transformedCenter = Vector3f(0.5f, 0.5f, 0.5f).mul(scale)
        rotation.transform(transformedCenter)
        return Transformation(Vector3f(transformedCenter).negate(), rotation, scale, Quaternionf())
    }

    private class Passive : BasePassive() {
        override val name = "<bold>테러"
        override val description = listOf(
            "<gray>패시브", "", "<gray>사망 시 자신 주변에 폭탄을 30개 소환한다.",
            "<gray>폭탄은 3초 후 폭발하며 주변에 있는 적에게 5의 피해를 입힌다."
        )
    }

    companion object {
        private val pendingUntil = java.util.WeakHashMap<Game, Long>()
        private val finishScheduled = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Game, Boolean>())

        fun pendingExplosionTicks(game: Game): Long =
            ((pendingUntil[game] ?: 0L) - org.bukkit.Bukkit.getCurrentTick().toLong()).coerceAtLeast(0L)

        fun markFinishScheduled(game: Game): Boolean = finishScheduled.add(game)

        fun clearPending(game: Game) {
            pendingUntil.remove(game)
            finishScheduled.remove(game)
        }
    }
}
