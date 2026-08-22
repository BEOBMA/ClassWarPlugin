package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Feather : GameClass() {
    override val name = "<gray>깃털"
    override val rank = Rank.C
    override val classItemMaterial = Material.FEATHER
    override var skills: List<Skill> = listOf()
    override var passives: List<BasePassive> = listOf(Passive())

    private class Passive : BasePassive(), GameStatusHandler {
        override val name = "<bold>가벼움"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>더 높게 점프하고, 느리게 낙하한다."
        )

        override fun onBattleStart() {
            refreshEffects()
            sounds.play(player, Sound.ENTITY_PARROT_FLY, volume = 0.75f, pitch = 1.6f)
            particles.spawn(player, Particle.CLOUD, count = 14, spread = 0.45, speed = 0.04)
        }

        override fun onGameTimePasses() {
            refreshEffects()
            if (player.location.clone().subtract(0.0, 0.08, 0.0).block.isPassable) {
                particles.spawn(player.location, Particle.WHITE_ASH, count = 5, spread = 0.3, speed = 0.005)
            }
        }

        private fun refreshEffects() {
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 40, 1, false, false, true))
            player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, true))
        }
    }
}
