package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Darkness : GameClass() {
    override val name = "<gray>어둠"
    override val rank = Rank.B
    override val classItemMaterial = Material.BLACK_CONCRETE
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf(Passive())

    private var artificialDarknessUntilTick = 0L

    private fun isInDarkness(): Boolean =
        player.world.fullTime < artificialDarknessUntilTick || player.eyeLocation.block.lightFromBlocks.toInt() == 0

    private inner class RedSkill : Skill() {
        override val name = "<bold>어둠 확산"
        override val description = listOf(
            "<gray>6초 동안 빛이 있는 곳에 있더라도 빛이 없는 곳으로 간주한다."
        )
        override val cooldown = 35

        override fun use() {
            artificialDarknessUntilTick = player.world.fullTime + 120L
            passives.filterIsInstance<Passive>().firstOrNull()?.refreshDarknessState()
            sounds.play(player, Sound.ENTITY_WARDEN_HEARTBEAT, volume = 0.75f, pitch = 0.55f)
            playerData.trackTask(object : BukkitRunnable() {
                var ticks = 0
                override fun run() {
                    if (ticks++ >= 120 || !player.isOnline || player.isDead) {
                        passives.filterIsInstance<Passive>().firstOrNull()?.refreshDarknessState()
                        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 0.45f, pitch = 0.6f)
                        cancel()
                        return
                    }
                    if (ticks % 3 == 0) {
                        particles.spawn(
                            player.location.clone().add(0.0, 1.0, 0.0),
                            Particle.DUST,
                            Particle.DustOptions(Color.fromRGB(12, 8, 20), 1.7f),
                            org.beobma.classWarPlugin.effect.ParticleOptions.spread(7, 0.75, 0.01),
                        )
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
        }
    }

    private inner class Passive : BasePassive(), GameStatusHandler, OnHitHandler {
        override val name = "<bold>어둠"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>빛이 없는 곳에서 {keyword:Stealth} 상태가 되며 기본 공격 피해가 2 증가한다."
        )

        private var grantedStealth: Stealth? = null

        override fun onBattleStart() = refreshDarknessState()

        override fun onGameTimePasses() = refreshDarknessState()

        fun refreshDarknessState() {
            if (isInDarkness()) {
                if (grantedStealth?.power == 1) return
                grantedStealth = playerData.addStatus(Stealth(), playerData) as Stealth
                grantedStealth?.applyStatus(powerSet = 1)
                particles.spawn(player, Particle.LARGE_SMOKE, count = 10, spread = 0.35, speed = 0.01)
                sounds.play(player, Sound.BLOCK_SCULK_SENSOR_CLICKING, volume = 0.35f, pitch = 0.55f)
            } else {
                grantedStealth?.remove()
                grantedStealth = null
            }
        }

        override fun onAttackHit(context: DamageContext) {
            if (!isInDarkness()) return
            context.addBaseDamage(2.0)
            particles.spawn(context.target.entity, Particle.SQUID_INK, count = 6, spread = 0.25)
            sounds.play(context.target.entity, Sound.ENTITY_PHANTOM_BITE, volume = 0.55f, pitch = 0.8f)
        }
    }
}
