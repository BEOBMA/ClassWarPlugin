package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getTargetCandidates
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val TRAPPER_TRAP_DAMAGE = 4.0
private const val TRAPPER_ANCHOR_RANGE = 6.0

class Trapper : GameClass() {
    override val name = "<gray>트래퍼"
    override val rank = Rank.B
    override val classItemMaterial = Material.STRING
    override var skills: List<Skill> = listOf(RedSkill())
    override var passives: List<BasePassive> = listOf()

    private class RedSkill : Skill() {
        override val name = "<bold>선"
        override val description = listOf(
            "<gray>${TRAPPER_ANCHOR_RANGE.toInt()}칸 내의 바라보는 블럭에 기준점을 설치한다.",
            "<gray>기준점을 2개 설치하면 기준점 사이에 보이지 않는 선을 만든다.",
            "<gray>적이 선을 통과하면 4의 피해를 입고 선이 제거된다.",
            "",
            "<dark_gray>선은 최대 10개까지 만들 수 있으며, 최대치를 초과한 경우 오래된 선을 제거하고 만든다.",
            "<dark_gray>선을 만들 때 기준점 사이의 거리가 10칸을 초과하거나 장애물로 막힌 경우 실패한다.",
        )
        override val cooldown = 0

        private data class TrapLine(val start: Location, val end: Location) {
            val bounds = BoundingBox(
                minOf(start.x, end.x) - 0.035,
                minOf(start.y, end.y) - 0.035,
                minOf(start.z, end.z) - 0.035,
                maxOf(start.x, end.x) + 0.035,
                maxOf(start.y, end.y) + 0.035,
                maxOf(start.z, end.z) + 0.035,
            )
        }

        private var firstAnchor: Location? = null
        private var selectedAnchor: Location? = null
        private val lines: ArrayDeque<TrapLine> = ArrayDeque()
        private var scannerTask: BukkitTask? = null
        private var visualTick = 0

        override fun isUseSuccess(): Boolean {
            val anchorRange = ClassBalanceManager.scaleRange(playerData, TRAPPER_ANCHOR_RANGE)
            val trace = player.world.rayTraceBlocks(
                player.eyeLocation,
                player.eyeLocation.direction,
                anchorRange,
                FluidCollisionMode.NEVER,
                true,
            )
            val hitPosition = trace?.hitPosition
            val hitFace = trace?.hitBlockFace
            if (hitPosition == null || hitFace == null) {
                player.sendMiniMessage(
                    "<red><bold>[!] %.1f칸 내의 블록을 바라봐야 합니다.".format(anchorRange)
                )
                return false
            }
            val candidate = hitPosition.toLocation(player.world)
                .add(hitFace.direction.multiply(0.075))
            val first = firstAnchor
            if (first != null) {
                if (first.world != candidate.world || first.distanceSquared(candidate) > 100.0) {
                    player.sendMiniMessage("<red><bold>[!] 두 기준점 사이의 거리는 10칸을 초과할 수 없습니다.")
                    return false
                }
                if (isPathBlocked(first, candidate)) {
                    player.sendMiniMessage("<red><bold>[!] 두 기준점 사이가 장애물로 막혀 있습니다.")
                    return false
                }
            }
            selectedAnchor = candidate
            return true
        }

        override fun use() {
            val anchor = selectedAnchor ?: return
            selectedAnchor = null
            ensureScanner()
            val first = firstAnchor
            if (first == null) {
                firstAnchor = anchor.clone()
                showAnchor(anchor, firstPoint = true)
                player.sendMiniMessage("<green><bold>[!] 첫 번째 기준점을 설치했습니다.")
                return
            }

            firstAnchor = null
            if (lines.size >= 10) {
                val removed = lines.removeFirst()
                particles.spawn(removed.start, Particle.SMOKE, count = 5, spread = 0.12)
                particles.spawn(removed.end, Particle.SMOKE, count = 5, spread = 0.12)
            }
            lines.addLast(TrapLine(first.clone(), anchor.clone()))
            showAnchor(anchor, firstPoint = false)
            particles.spawn(first, Particle.ENCHANT, count = 12, spread = 0.18, speed = 0.02)
            sounds.play(first, Sound.BLOCK_TRIPWIRE_ATTACH, volume = 0.85f, pitch = 1.2f)
            sounds.play(anchor, Sound.BLOCK_TRIPWIRE_ATTACH, volume = 0.85f, pitch = 1.35f)
            player.sendMiniMessage("<green><bold>[!] 보이지 않는 선을 설치했습니다. <gray>(${lines.size}/10)")
        }

        private fun ensureScanner() {
            if (scannerTask != null) return
            scannerTask = playerData.trackTask(object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline || player.isDead) {
                        scannerTask = null
                        cancel()
                        return
                    }
                    if (firstAnchor == null && lines.isEmpty()) {
                        scannerTask = null
                        cancel()
                        return
                    }
                    if (visualTick++ % 10 == 0) {
                        firstAnchor?.let { spawnPrivateAnchorParticle(it) }
                        lines.forEach {
                            spawnPrivateAnchorParticle(it.start)
                            spawnPrivateAnchorParticle(it.end)
                        }
                    }
                    if (lines.isEmpty()) return

                    val candidates = playerData.getTargetCandidates().filter(::isEnemyCandidate)
                    val iterator = lines.iterator()
                    while (iterator.hasNext()) {
                        val line = iterator.next()
                        val target = candidates.firstOrNull { candidate ->
                            val candidateBox = candidate.entity.boundingBox
                            candidateBox.overlaps(line.bounds) && HitboxUtil.intersectsSegment(
                                candidateBox,
                                line.start.toVector(),
                                line.end.toVector(),
                                expansion = 0.035,
                            )
                        } ?: continue
                        iterator.remove()
                        trigger(line, target)
                    }
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
        }

        private fun isEnemyCandidate(candidate: EntityData): Boolean {
            if (candidate == playerData || candidate.entityStatus.isDead || !candidate.entityStatus.isSkillTargeting) return false
            return candidate !is PlayerData || playerData.isEnemyOf(candidate)
        }

        private fun trigger(line: TrapLine, target: EntityData) {
            target.damage(TRAPPER_TRAP_DAMAGE, DamageType.Normal, playerData)
            particles.line(
                line.start,
                line.end,
                Particle.DUST,
                Particle.DustOptions(Color.RED, 0.85f),
                spacing = 0.24,
                options = ParticleOptions(count = 1),
            )
            particles.spawn(
                target.entity.location.clone().add(0.0, target.entity.height / 2.0, 0.0),
                Particle.DUST,
                Particle.DustOptions(Color.RED, 1.25f),
                ParticleOptions(count = 18, offsetX = 0.38, offsetY = 0.38, offsetZ = 0.38, speed = 0.05),
            )
            particles.spawn(target.entity, Particle.CRIT, count = 12, spread = 0.3, speed = 0.05)
            sounds.play(target.entity, Sound.BLOCK_TRIPWIRE_CLICK_ON, volume = 1.0f, pitch = 0.75f)
            sounds.play(target.entity, Sound.ENTITY_PLAYER_HURT, volume = 0.55f, pitch = 1.55f)
        }

        private fun isPathBlocked(first: Location, second: Location): Boolean {
            val difference = second.toVector().subtract(first.toVector())
            val distance = difference.length()
            if (distance <= 0.2) return false
            val direction = difference.normalize()
            val start = first.clone().add(direction.clone().multiply(0.1))
            return start.world.rayTraceBlocks(
                start,
                direction,
                (distance - 0.2).coerceAtLeast(0.0),
                FluidCollisionMode.NEVER,
                true,
            ) != null
        }

        private fun showAnchor(anchor: Location, firstPoint: Boolean) {
            particles.spawn(
                anchor,
                Particle.DUST,
                Particle.DustOptions(if (firstPoint) Color.YELLOW else Color.ORANGE, 1.35f),
                ParticleOptions(count = 14, offsetX = 0.12, offsetY = 0.12, offsetZ = 0.12),
            )
            sounds.play(anchor, Sound.BLOCK_TRIPWIRE_ATTACH, volume = 0.8f, pitch = if (firstPoint) 1.0f else 1.35f)
        }

        private fun spawnPrivateAnchorParticle(anchor: Location) {
            particles.spawnTo(player, anchor, Particle.WAX_ON, count = 1, spread = 0.025)
        }
    }
}
