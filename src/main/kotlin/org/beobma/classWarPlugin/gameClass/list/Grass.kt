package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.util.BoundingBox
import kotlin.math.floor

class Grass : GameClass(), GameStatusHandler {
    override val name = "<gray>풀"
    override val rank = Rank.C
    override val classItemMaterial = Material.SHORT_GRASS
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive(), PassiveTwo())
    private var plantStealth: Stealth? = null

    override fun onBattleStart() {
        plantStealth?.remove()
        plantStealth = null
    }

    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        val touching = touchesPlant()
        if (!touching) {
            plantStealth?.remove()
            plantStealth = null
            return
        }
        if (plantStealth == null || plantStealth?.power == 0) {
            plantStealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also {
                it.applyStatus(powerSet = 1)
            }
            sounds.play(player, Sound.BLOCK_GRASS_STEP, volume = 0.55f, pitch = 1.25f)
        }
        particles.spawn(player.location.clone().add(0.0, 0.15, 0.0), Particle.HAPPY_VILLAGER, count = 8, spread = 0.55, speed = 0.01)
        playerData.radius(player.location, TargetType.Enemy, 4.0, false).forEach { target ->
            target.damage(1.0, DamageType.StatusAbnormality, playerData, false, damagePath = DamagePath.STATUS_EFFECT)
            particles.spawn(target.entity, Particle.DAMAGE_INDICATOR, count = 4, spread = 0.3, speed = 0.04)
        }
    }

    private fun touchesPlant(): Boolean {
        val box = player.boundingBox.expand(0.03)
        for (x in floor(box.minX).toInt()..floor(box.maxX).toInt()) {
            for (y in floor(box.minY).toInt()..floor(box.maxY).toInt()) {
                for (z in floor(box.minZ).toInt()..floor(box.maxZ).toInt()) {
                    val block = player.world.getBlockAt(x, y, z)
                    if (!isPlant(block.type)) continue
                    if (box.overlaps(BoundingBox(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0))) return true
                }
            }
        }
        return false
    }

    private fun isPlant(material: Material): Boolean {
        val id = material.name
        return id.endsWith("_FLOWER") || id.endsWith("_TULIP") || id in setOf(
            "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DANDELION", "POPPY",
            "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "OXEYE_DAISY", "CORNFLOWER",
            "LILY_OF_THE_VALLEY", "WITHER_ROSE", "PINK_PETALS", "WILDFLOWERS",
            "SEAGRASS", "TALL_SEAGRASS", "MOSS_CARPET", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY"
        )
    }

    private class Passive : BasePassive() {
        override val name = "<bold>그냥 풀"
        override val description = listOf("<gray>패시브", "", "<gray>꽃, 풀 종류인 어떤 블럭에든 닿아있다면", "<gray>자신은 {keyword:Stealth} 상태가 된다.")
    }
    private class PassiveTwo : BasePassive() {
        override val name = "<bold>가시"
        override val description = listOf("<gray>패시브", "", "<gray>그냥 풀의 효과가 발동중일 때", "<gray>자신 주위 4칸 이내에 있는 적은 매 초마다 1의 피해를 입는다.")
    }
}
