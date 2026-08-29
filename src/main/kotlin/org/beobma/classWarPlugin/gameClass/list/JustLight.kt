package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.Light
import org.bukkit.scheduler.BukkitRunnable

class JustLight : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>그저 빛"
    override val rank = Rank.C
    override val classItemMaterial = Material.GLOWSTONE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    private val lightBlocks = linkedMapOf<Block, BlockData>()
    private val lightOffsets = listOf(
        Triple(0, 0, 0),
        Triple(6, 0, 0), Triple(-6, 0, 0), Triple(0, 0, 6), Triple(0, 0, -6),
        Triple(4, 0, 4), Triple(4, 0, -4), Triple(-4, 0, 4), Triple(-4, 0, -4),
        Triple(0, 5, 0), Triple(0, -4, 0),
    )

    override fun onBattleStart() {
        refreshLight()
        playerData.trackTask(object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    clearLight()
                    cancel()
                    return
                }
                refreshLight()
                if (player.world.fullTime % 6L == 0L) {
                    particles.spawn(player, Particle.GLOW, count = 10, spread = 2.5, speed = 0.015)
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
        sounds.play(player, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.55f, pitch = 1.7f)
    }

    override fun onGameTimePasses() = refreshLight()
    override fun onGameEnd() = clearLight()
    override fun onPlayerDeath() = clearLight()

    private fun refreshLight() {
        val center = player.eyeLocation.block
        val desired = lightOffsets.mapNotNullTo(linkedSetOf()) { (x, y, z) ->
            findLightDestination(center.getRelative(x, y, z))
        }
        lightBlocks.keys.filter { it !in desired }.toList().forEach(::restoreLight)
        desired.filter { it !in lightBlocks }.forEach { destination ->
            if (!destination.type.isAir) return@forEach
            lightBlocks[destination] = destination.blockData.clone()
            destination.setBlockData(maximumLightData(), false)
        }
    }

    private fun clearLight() {
        lightBlocks.keys.toList().forEach(::restoreLight)
    }

    private fun findLightDestination(preferred: Block): Block? =
        listOf(preferred, preferred.getRelative(0, 1, 0), preferred.getRelative(0, -1, 0))
            .firstOrNull { candidate -> candidate in lightBlocks || candidate.type.isAir }

    private fun maximumLightData(): BlockData = (Material.LIGHT.createBlockData() as Light).apply {
        level = 15
    }

    private fun restoreLight(block: Block) {
        val original = lightBlocks.remove(block) ?: return
        if (block.type == Material.LIGHT) block.setBlockData(original, false)
    }

    private class Passive : BasePassive() {
        override val name = "<bold>발광"
        override val description = listOf("<gray>패시브", "", "<gray>자신 주위에 강한 빛을 생성한다.")
    }
}
