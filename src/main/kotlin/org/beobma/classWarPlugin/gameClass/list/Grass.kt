package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.getTargetCandidates
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.entity.EntityDamageEvent
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import kotlin.math.floor

private const val GRASS_THORN_RADIUS = 4.0
private const val GRASS_STEALTH_SUPPRESSION_TICKS = 60L

class Grass : GameClass(), GameStatusHandler, OnHitHandler, WhenHitHandler, EnvironmentalDamageHandler {
    override val classId = "grass"
    override val name = "<gray>풀"
    override val rank = Rank.C
    override val classItemMaterial = Material.SHORT_GRASS
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive(), PassiveTwo())
    private var plantStealth: Stealth? = null
    private var stealthSuppressedUntilTick = 0L
    private var suppressionTask: BukkitTask? = null

    override fun onBattleStart() {
        suppressionTask?.cancel()
        suppressionTask = null
        stealthSuppressedUntilTick = 0L
        removePlantStealth()
    }

    override fun onGameTimePasses() {
        if (!player.isOnline || playerStatus.isDead) return
        if (!refreshPlantStealth()) return
        particles.spawn(player.location.clone().add(0.0, 0.15, 0.0), Particle.HAPPY_VILLAGER, count = 8, spread = 0.55, speed = 0.01)

        nearbyEnemies().forEach { target ->
            target.damage(1.0, DamageType.StatusAbnormality, playerData, false, damagePath = DamagePath.STATUS_EFFECT)
            particles.spawn(target.entity, Particle.DAMAGE_INDICATOR, count = 4, spread = 0.3, speed = 0.04)
        }
    }

    override fun onHit(context: DamageContext) {
        if (context.damage > 0.0) suppressStealth()
    }

    override fun whenHit(context: DamageContext) {
        if (context.damage > 0.0) suppressStealth()
    }

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event.finalDamage > 0.0) suppressStealth()
    }

    fun suppressStealthFromDamage() = suppressStealth()

    private fun refreshPlantStealth(): Boolean {
        if (!touchesPlant() || game.combatTick < stealthSuppressedUntilTick) {
            removePlantStealth()
            return false
        }
        if (plantStealth?.let { it.power > 0 && playerData.statusAbnormalitys.contains(it) } != true) {
            plantStealth = (playerData.addStatus(Stealth(), playerData) as Stealth).also {
                it.applyStatus(powerSet = 1)
            }
            sounds.play(player, Sound.BLOCK_GRASS_STEP, volume = 0.55f, pitch = 1.25f)
        }
        return true
    }

    private fun suppressStealth() {
        stealthSuppressedUntilTick = game.combatTick + GRASS_STEALTH_SUPPRESSION_TICKS
        val wasStealthed = playerData.statusAbnormalitys.any { it is Stealth && it.power > 0 }
        playerData.statusAbnormalitys.filterIsInstance<Stealth>().toList().forEach { it.remove() }
        plantStealth = null
        if (wasStealthed) {
            particles.spawn(player, Particle.LARGE_SMOKE, count = 14, spread = 0.45, speed = 0.025)
            sounds.play(player, Sound.BLOCK_GRASS_BREAK, volume = 0.5f, pitch = 0.72f)
        }

        suppressionTask?.cancel()
        suppressionTask = playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                suppressionTask = null
                if (!player.isOnline || playerStatus.isDead) return
                if (game.combatTick < stealthSuppressedUntilTick) return
                refreshPlantStealth()
            }
        }.runTaskLater(ClassWarPlugin.instance, GRASS_STEALTH_SUPPRESSION_TICKS))
    }

    private fun nearbyEnemies(): List<EntityData> {
        val training = PlayerTagManager.isTraining(player)
        val sourceBox = player.boundingBox
        return playerData.getTargetCandidates().filter { target ->
            if (target == playerData || target.entityStatus.isDead || !target.entityStatus.isSkillTargeting) return@filter false
            if (!target.entity.isValid || target.entity.world != player.world) return@filter false
            val isEnemy = when (target) {
                is PlayerData -> playerData.isEnemyOf(target)
                else -> training
            }
            isEnemy && HitboxUtil.distanceSquared(sourceBox, target.entity.boundingBox) <=
                GRASS_THORN_RADIUS * GRASS_THORN_RADIUS
        }
    }

    private fun removePlantStealth() {
        plantStealth?.remove()
        plantStealth = null
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
        override val description = listOf(
            "<gray>패시브", "", "<gray>꽃, 풀 종류인 어떤 블럭에든 닿아있다면 자신은 {keyword:Stealth} 상태가 된다.",
            "<gray>피해를 주거나 받으면 3초간 {keyword:Stealth} 상태가 될 수 없다."
        )
    }
    private class PassiveTwo : BasePassive() {
        override val name = "<bold>가시"
        override val description = listOf(
            "<gray>패시브", "", "<gray>그냥 풀의 효과가 발동 중일 때",
            "<gray>자신 주위 4칸 이내에 있는 적은 매 초마다 1의 피해를 입는다."
        )
    }
}
