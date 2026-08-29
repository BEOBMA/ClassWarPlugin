package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.floor

class Terra : PlanetClass(), GameStatusHandler {
    override val name = "<gray>지구"
    override val rank = Rank.A
    override val classItemMaterial = Material.GRASS_BLOCK
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    override fun onBattleStart() {
        playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    cancel()
                    return
                }
                if (!isPowerEnabled() || game.isPaused || !isNearNature()) return
                particles.spawn(player.location.clone().add(0.0, 0.35, 0.0),
                    Particle.HAPPY_VILLAGER, count = 3, spread = 0.38, speed = 0.012)
                particles.spawn(player.location.clone().add(0.0, 0.2, 0.0),
                    Particle.SPORE_BLOSSOM_AIR, count = 2, spread = 0.45, speed = 0.006)
                val maximum = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                if (player.health >= maximum) return
                playerData.heal(0.5, DamageType.Normal, playerData)
                particles.spawn(player.location.clone().add(0.0, 0.45, 0.0), Particle.HAPPY_VILLAGER, count = 5, spread = 0.5, speed = 0.025)
                sounds.play(player, Sound.BLOCK_GRASS_STEP, volume = 0.22f, pitch = 1.55f)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L))
    }

    override fun onGameTimePasses() = Unit

    private fun isNearNature(): Boolean {
        val box = player.boundingBox.expand(1.5, 1.0, 1.5)
        for (x in floor(box.minX).toInt()..floor(box.maxX).toInt()) {
            for (y in floor(box.minY).toInt()..floor(box.maxY).toInt()) {
                for (z in floor(box.minZ).toInt()..floor(box.maxZ).toInt()) {
                    if (isNature(player.world.getBlockAt(x, y, z).type)) return true
                }
            }
        }
        return false
    }

    private fun isNature(material: Material): Boolean {
        val id = material.name
        return material == Material.WATER || id.contains("GRASS") || id.contains("DIRT") ||
            id.contains("MUD") || id.contains("MOSS") || id.contains("LEAVES") ||
            id.contains("LOG") || id.contains("WOOD") || id.contains("SAPLING") ||
            id.contains("FLOWER") || id.contains("TULIP") || id.contains("FERN") ||
            id.contains("VINE") || id.contains("ROOT") || id.contains("SEAGRASS") ||
            id.contains("KELP") || id in setOf("DANDELION", "POPPY", "ALLIUM", "AZURE_BLUET",
                "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY")
    }

    private class Passive : BasePassive() {
        override val name = "<bold>지구"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>체력 재생 조건이 5초로 완화된다.",
            "<gray>식물, 잔디, 물, 흙 블럭 주위에 있을 때 매 초마다 체력을 0.5씩 회복한다."
        )
    }
}
