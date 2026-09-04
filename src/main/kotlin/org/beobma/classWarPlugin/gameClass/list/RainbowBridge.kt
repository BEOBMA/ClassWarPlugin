package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.entity.Snowball
import org.bukkit.event.entity.ProjectileHitEvent
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val RAINBOW_BRIDGE_COOLDOWN_SECONDS = 40
private const val RAINBOW_BRIDGE_DURATION_TICKS = 200
private const val RAINBOW_SNOWBALL_TAG = "classwar_rainbow_bridge"

class RainbowBridge : GameClass(), GameEndHandler, PlayerDeathHandler {
    override val classId = "rainbow-bridge"
    override val name = "<gray>무지개 다리"
    override val rank = Rank.B
    override val classItemMaterial = Material.PINK_GLAZED_TERRACOTTA
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives = emptyList<org.beobma.classWarPlugin.skill.Passive>()
    private data class ClaimedBlock(val original: BlockState, var references: Int)
    private val claimedBlocks = mutableMapOf<Block, ClaimedBlock>()
    private val activeCasts = mutableMapOf<UUID, Set<Block>>()
    private val activeProjectiles = mutableSetOf<Snowball>()
    private val rainbowMaterials = setOf(
        Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
        Material.LIME_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS,
    )

    override fun onGameEnd() = clearAllBridges()
    override fun onPlayerDeath() = clearAllBridges()

    private inner class RedSkill : Skill() {
        override val definitionId = "rainbow-bridge/red-skill"
        override val name = "<bold>무지개"
        override val description = listOf(
            "<gray>바라보는 방향으로 눈덩이를 던져 경로를 따라 10초간 무지개 다리를 설치한다.",
            "<gray>무지개 다리 위에서 자신은 지속적으로 체력을 조금씩 회복한다."
        )
        override val cooldown = RAINBOW_BRIDGE_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val forward = player.eyeLocation.direction.clone().apply {
                y = y.coerceIn(-0.42, 0.42)
            }.normalize()
            val horizontal = forward.clone().setY(0.0).takeIf { it.lengthSquared() > 1.0E-8 }?.normalize()
                ?: Vector(0.0, 0.0, 1.0)
            val right = horizontal.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
            val start = player.eyeLocation.clone().add(forward.clone().multiply(0.35))
            val centers = mutableListOf<Location>()
            val centerKeys = mutableSetOf<String>()
            val hitboxes = mutableListOf<BoundingBox>()
            val colors = listOf(Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.BLUE, Color.PURPLE)
            val materials = rainbowMaterials.toList()
            val castId = UUID.randomUUID()
            val castBlocks = linkedSetOf<Block>()
            activeCasts[castId] = castBlocks

            fun addBridgeCenter(projectilePoint: Location) {
                val center = projectilePoint.clone().subtract(0.0, 1.55, 0.0)
                val key = "${(center.x * 2.0).roundToInt()}:${(center.y * 2.0).roundToInt()}:${(center.z * 2.0).roundToInt()}"
                if (!centerKeys.add(key)) return
                centers += center
                hitboxes += BoundingBox(center.x - 2.8, center.y - 0.3, center.z - 2.8,
                    center.x + 2.8, center.y + 0.75, center.z + 2.8)
                materials.forEachIndexed { lane, material ->
                    val offset = (lane - 3) * 0.72
                    val bridgeBlock = center.clone().add(right.clone().multiply(offset)).subtract(0.0, 0.25, 0.0).block
                    claimBlock(bridgeBlock, material, castBlocks)
                }
                colors.forEachIndexed { colorIndex, color ->
                    val offset = (colorIndex - 3) * 0.32
                    val point = center.clone().add(right.clone().multiply(offset)).add(0.0, 0.08, 0.0)
                    particles.spawn(point, Particle.DUST, Particle.DustOptions(color, 1.25f), ParticleOptions())
                }
            }

            fun extendBridge(from: Location, to: Location) {
                val delta = to.toVector().subtract(from.toVector())
                val points = ceil(delta.length() / 0.45).toInt().coerceAtLeast(1)
                repeat(points) { index ->
                    addBridgeCenter(from.clone().add(delta.clone().multiply((index + 1.0) / points)))
                }
            }

            val snowball = player.world.spawn(start, Snowball::class.java).apply {
                shooter = player
                velocity = forward.clone().multiply(1.35)
                isPersistent = false
                addScoreboardTag(RAINBOW_SNOWBALL_TAG)
            }
            activeProjectiles += snowball
            addBridgeCenter(start)
            sounds.play(start, Sound.ENTITY_SNOWBALL_THROW, volume = 1.0f, pitch = 1.25f)
            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                var tick = 0
                var bridgeAge = 0
                var previous = start.clone()
                var projectileFlying = true
                override fun run() {
                    if (castId !in activeCasts) {
                        if (snowball.isValid) snowball.remove()
                        activeProjectiles.remove(snowball)
                        cancel()
                        return
                    }
                    if (!player.isOnline || playerStatus.isDead || (!projectileFlying && bridgeAge >= RAINBOW_BRIDGE_DURATION_TICKS)) {
                        if (snowball.isValid) snowball.remove()
                        activeProjectiles.remove(snowball)
                        releaseCast(castId)
                        sounds.play(start, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.55f, pitch = 0.8f)
                        cancel()
                        return
                    }

                    if (projectileFlying) {
                        if (!snowball.isValid || snowball.isDead) {
                            val impact = snowball.location.clone()
                            if (impact.world == previous.world && impact.distanceSquared(previous) > 1.0E-4) {
                                extendBridge(previous, impact)
                                previous = impact
                            }
                            projectileFlying = false
                            activeProjectiles.remove(snowball)
                            sounds.play(previous, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.75f, pitch = 1.5f)
                        } else {
                            val current = snowball.location.clone()
                            val segmentLength = current.distance(previous)
                            if (segmentLength > 0.01) {
                                extendBridge(previous, current)
                                previous = current
                            }
                        }
                    }

                    if (tick % 2 == 0) centers.forEachIndexed { index, center ->
                        if ((tick + index) % 8 == 0) particles.spawn(center, Particle.END_ROD, count = 1, spread = 0.18)
                        val color = colors[(index + tick / 2) % colors.size]
                        particles.spawn(center.clone().add(0.0, 0.1, 0.0), Particle.DUST,
                            Particle.DustOptions(color, 0.9f), ParticleOptions())
                    }
                    if (tick % 10 == 0 && hitboxes.any { it.overlaps(player.boundingBox) }) {
                        playerData.heal(0.45, DamageType.Normal, playerData)
                        particles.spawn(player, Particle.HEART, count = 2, spread = 0.3)
                        sounds.playTo(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume = 0.25f, pitch = 1.8f)
                    }
                    if (!projectileFlying) bridgeAge++
                    tick++
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            return true
        }
    }

    private fun claimBlock(block: Block, material: Material, castBlocks: MutableSet<Block>) {
        if (!castBlocks.add(block)) return
        claimedBlocks[block]?.let { claimed ->
            claimed.references++
            return
        }
        if (!block.type.isAir) {
            castBlocks.remove(block)
            return
        }
        val original = block.state
        block.setType(material, false)
        claimedBlocks[block] = ClaimedBlock(original, 1)
    }

    private fun releaseCast(castId: UUID) {
        activeCasts.remove(castId)?.forEach { block ->
            val claimed = claimedBlocks[block] ?: return@forEach
            claimed.references--
            if (claimed.references <= 0) {
                if (block.type in rainbowMaterials) claimed.original.update(true, false)
                claimedBlocks.remove(block)
            }
        }
    }

    private fun clearAllBridges() {
        activeProjectiles.toList().forEach { if (it.isValid) it.remove() }
        activeProjectiles.clear()
        claimedBlocks.entries.toList().asReversed().forEach { (block, claimed) ->
            if (block.type in rainbowMaterials) claimed.original.update(true, false)
        }
        claimedBlocks.clear()
        activeCasts.clear()
    }

    companion object {
        /** 무지개 눈덩이는 엔티티를 통과하고 지형에 닿았을 때만 경로 생성을 끝낸다. */
        fun handleProjectileHit(event: ProjectileHitEvent): Boolean {
            val projectile = event.entity as? Snowball ?: return false
            if (RAINBOW_SNOWBALL_TAG !in projectile.scoreboardTags || event.hitBlock != null || event.hitEntity == null) return false
            event.isCancelled = true
            val velocity = projectile.velocity
            if (velocity.lengthSquared() > 1.0E-6) {
                projectile.teleport(projectile.location.add(velocity.clone().normalize().multiply(0.65)))
                projectile.velocity = velocity
            }
            return true
        }
    }
}
