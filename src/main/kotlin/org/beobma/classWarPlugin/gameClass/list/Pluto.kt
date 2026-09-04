package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable

private const val PLUTO_SKILL_COOLDOWN_SECONDS = 40
private const val PLUTO_DURATION_TICKS = 15L * 20L

class Pluto : PlanetClass(), OnHitHandler, GameEndHandler {
    private var solarCopy = false
    internal fun asSolarCopy(): Pluto = apply { solarCopy = true }
    override val classId = "pluto"
    override val name = "<gray>명왕성"
    override val rank = Rank.B
    override val classItemMaterial = Material.ORANGE_CONCRETE_POWDER
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = emptyList()

    private var miniature = false
    private var scaleEffect: AutoCloseable? = null

    override fun onHit(context: DamageContext) {
        if (miniature && isPowerEnabled()) context.addDamageDealtMultiplier(0.5)
    }

    override fun onGameEnd() {
        restoreScale()
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "pluto/red-skill"
        override fun matchesId(candidate: String): Boolean = super.matchesId(candidate) || (solarCopy && candidate == "${javaClass.name}:solar")
        override val name = "<bold>명왕성"
        override val description = listOf(
            "<gray>15초간 자신의 크기가 95% 감소한다.",
            "<gray>지속 시간동안 가하는 피해가 50% 감소한다."
        )
        override val cooldown = PLUTO_SKILL_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (!isPowerEnabled()) {
                player.sendMiniMessage("<red><bold>[!] 명왕성이 파괴되어 능력을 사용할 수 없습니다.")
                return false
            }
            if (miniature) {
                player.sendMiniMessage("<red><bold>[!] 이미 크기가 감소한 상태입니다.")
                return false
            }
            return true
        }

        override fun use(): Boolean {
            if (player.getAttribute(Attribute.SCALE) == null) return false
            scaleEffect = playerData.attributeEffects.multiply(abilityScope, Attribute.SCALE, 0.05)
            miniature = true
            particles.spawn(player, Particle.POOF, count = 42, spread = 0.8, speed = 0.12)
            particles.spawn(player, Particle.REVERSE_PORTAL, count = 30, spread = 0.65, speed = 0.08)
            sounds.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.7f, pitch = 1.8f)

            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                var elapsedTicks = 0L
                override fun run() {
                    if (!player.isOnline || playerStatus.isDead || !isPowerEnabled()) {
                        restoreScale()
                        cancel()
                        return
                    }
                    if (game.isPaused) return
                    elapsedTicks += 2L
                    if (elapsedTicks >= PLUTO_DURATION_TICKS) {
                        restoreScale()
                        cancel()
                        return
                    }
                    particles.spawn(player.location.clone().add(0.0, player.height * 0.5, 0.0),
                        Particle.REVERSE_PORTAL, count = 3, spread = 0.18, speed = 0.025)
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 2L))
            return true
        }
    }

    private fun restoreScale() {
        if (!miniature && scaleEffect == null) return
        scaleEffect?.close()
        miniature = false
        scaleEffect = null
        if (player.isOnline && !playerStatus.isDead) {
            particles.spawn(player, Particle.POOF, count = 28, spread = 0.55, speed = 0.08)
            sounds.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.45f, pitch = 1.15f)
        }
    }
}
