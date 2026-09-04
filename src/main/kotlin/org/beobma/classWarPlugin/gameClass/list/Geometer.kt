package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.*
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import org.bukkit.util.BoundingBox
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

// 밸런스 조정 상수
private const val GEOMETER_COORDINATE_COOLDOWN_SECONDS = 1
private const val GEOMETER_COMPRESSION_COOLDOWN_SECONDS = 8
private const val GEOMETER_COORDINATE_RANGE = 50.0
private const val GEOMETER_MAX_AXIS_LENGTH = 24.0
private const val GEOMETER_MAX_VOLUME = 4096.0
private const val GEOMETER_MAX_COMPRESSION_DAMAGE = 16.0
private const val GEOMETER_MIN_COMPRESSION_DAMAGE = 5.0

class Geometer : GameClass() {
    override val classId = "geometer"
    override val name = "<gray>기하학자"
    override val rank = Rank.A
    override val classItemMaterial = Material.SHULKER_SHELL

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf()

    private var first: Location? = null
    private var second: Location? = null
    private var boxDisplayTask: BukkitTask? = null

    private fun clearCoordinates(playSound: Boolean = true) {
        boxDisplayTask?.cancel()
        boxDisplayTask = null
        first = null; second = null
        if (playSound) sounds.play(player, Sound.BLOCK_BEACON_DEACTIVATE, pitch = 1.4f)
    }

    private fun volume(a: Location, b: Location): Double =
        max(1.0, abs(a.x - b.x)) * max(1.0, abs(a.y - b.y)) * max(1.0, abs(a.z - b.z))

    private fun validBox(a: Location, b: Location): Boolean = a.world == b.world &&
        abs(a.x - b.x) <= GEOMETER_MAX_AXIS_LENGTH &&
        abs(a.y - b.y) <= GEOMETER_MAX_AXIS_LENGTH &&
        abs(a.z - b.z) <= GEOMETER_MAX_AXIS_LENGTH &&
        volume(a, b) <= GEOMETER_MAX_VOLUME

    private fun compressionDamage(a: Location, b: Location): Double =
        max(GEOMETER_MIN_COMPRESSION_DAMAGE, GEOMETER_MAX_COMPRESSION_DAMAGE - sqrt(volume(a, b)))

    private fun showBox(a: Location, b: Location) {
        val minX = minOf(a.x, b.x); val maxX = maxOf(a.x, b.x)
        val minY = minOf(a.y, b.y); val maxY = maxOf(a.y, b.y)
        val minZ = minOf(a.z, b.z); val maxZ = maxOf(a.z, b.z)
        fun edge(from: Location, to: Location) {
            particles.line(from, to, Particle.END_ROD, spacing = 0.9)
        }

        listOf(minY, maxY).forEach { y ->
            edge(Location(a.world, minX, y, minZ), Location(a.world, maxX, y, minZ))
            edge(Location(a.world, minX, y, maxZ), Location(a.world, maxX, y, maxZ))
            edge(Location(a.world, minX, y, minZ), Location(a.world, minX, y, maxZ))
            edge(Location(a.world, maxX, y, minZ), Location(a.world, maxX, y, maxZ))
        }
        listOf(minX to minZ, minX to maxZ, maxX to minZ, maxX to maxZ).forEach { (x, z) ->
            edge(Location(a.world, x, minY, z), Location(a.world, x, maxY, z))
        }
    }

    private fun startBoxDisplay(a: Location, b: Location) {
        boxDisplayTask?.cancel()
        boxDisplayTask = playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (first == null || second == null) { cancel(); return }
                showBox(a, b)
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 10L))
    }

    private fun scaledCorner(corner: Location, center: Location, scale: Double): Location {
        val offset = corner.toVector().subtract(center.toVector()).multiply(scale)
        return center.clone().add(offset)
    }

    private fun burstParticles(center: Location, radius: Double) {
        val points = 14
        repeat(points) { index ->
            val y = 1.0 - 2.0 * (index + 0.5) / points
            val horizontal = sqrt(1.0 - y * y)
            val angle = PI * (3.0 - sqrt(5.0)) * index
            val direction = Vector(cos(angle) * horizontal, y, sin(angle) * horizontal)
            val location = center.clone().add(direction.multiply(radius))
            particles.spawn(location, Particle.END_ROD, count = 1)
        }
    }

    private inner class RedSkill : Skill() {
        override val definitionId = "geometer/red-skill"
        override val name = "<bold>좌표 설정"
        override val description = listOf(
            "<gray>${GEOMETER_COORDINATE_RANGE.toInt()}칸 내의 바라보는 블럭에 좌표를 지정한다.",
            "",
            "<gray>첫 번째 좌표와 두 번째 좌표가 지정되면",
            "<gray>두 좌표를 꼭짓점으로 하는 직육면체가 생성된다.",
            "",
            "<gray>직육면체의 부피는 최대 ${GEOMETER_MAX_VOLUME.toInt()}이며",
            "<gray>각 변의 길이는 ${GEOMETER_MAX_AXIS_LENGTH.toInt()}칸을 초과할 수 없다.",
            "",
            "<dark_gray>웅크린 상태에서 사용하면 모든 좌표를 제거한다."
        )
        override val cooldown = GEOMETER_COORDINATE_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (player.isSneaking) { clearCoordinates(); return true }
            val targetBlock = playerData.shotLaserGetBlock(GEOMETER_COORDINATE_RANGE) ?: return false
            val point = targetBlock.location.add(0.5, 1.0, 0.5)
            val firstPoint = first
            if (firstPoint == null || second != null) {
                if (second != null) clearCoordinates(playSound = false)
                first = point; second = null
            }
            else if (validBox(firstPoint, point)) second = point
            else {
                player.sendMiniMessage(
                    "<red><bold>[!] 부피 ${GEOMETER_MAX_VOLUME.toInt()}, " +
                        "변 길이 ${GEOMETER_MAX_AXIS_LENGTH.toInt()} 제한을 초과합니다."
                )
                return false
            }
            particles.spawn(point, Particle.HAPPY_VILLAGER, count = 12, spread = 0.2)
            sounds.play(point, Sound.BLOCK_NOTE_BLOCK_PLING, pitch = if (second == null) 1.0f else 1.6f)
            second?.let { secondPoint ->
                val selectedFirstPoint = first ?: return@let
                showBox(selectedFirstPoint, secondPoint)
                startBoxDisplay(selectedFirstPoint.clone(), secondPoint.clone())
            }
            return true
        }

        override fun isUseSuccess(): Boolean {
            if (player.isSneaking || playerData.shotLaserGetBlock(GEOMETER_COORDINATE_RANGE) != null) return true
            player.sendMiniMessage(
                "<red><bold>[!] ${GEOMETER_COORDINATE_RANGE.toInt()}칸 내에 바라보는 블럭이 없습니다."
            )
            return false
        }
    }

    private inner class OrangeSkill : Skill() {
        override val definitionId = "geometer/orange-skill"
        override val name = "<bold>압축"
        override val description = listOf(
            "<gray>직육면체를 중심으로 압축한다.",
            "<gray>내부의 모든 적에게 직육면체의 부피에 반비례하여 피해를 입힌다.",
            "<gray>이후 직육면체와 좌표가 모두 제거된다.",
            "",
            "<dark_gray>피해량은 최대 (5)와 (16 - √부피) 중 큰 값으로 결정된다.",
            "<dark_gray>"
        )
        override val cooldown = GEOMETER_COMPRESSION_COOLDOWN_SECONDS

        override fun use(): Boolean {
            val a = first ?: return false
            val b = second ?: return false
            val damage = compressionDamage(a, b)
            val minX = minOf(a.x, b.x); val maxX = maxOf(a.x, b.x)
            val minY = minOf(a.y, b.y); val maxY = maxOf(a.y, b.y)
            val minZ = minOf(a.z, b.z); val maxZ = maxOf(a.z, b.z)
            val center = Location(a.world, (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
            val targetRadius = sqrt(
                ((maxX - minX) / 2) * ((maxX - minX) / 2) +
                    ((maxY - minY) / 2) * ((maxY - minY) / 2) +
                    ((maxZ - minZ) / 2) * ((maxZ - minZ) / 2)
            ) + 1.0
            boxDisplayTask?.cancel()
            boxDisplayTask = null
            first = null; second = null
            sounds.play(center, Sound.BLOCK_PISTON_CONTRACT, volume = 1.4f, pitch = 0.6f)

            playerData.trackTask(object : BukkitRunnable(abilityScope) {
                var tick = 0
                override fun run() {
                    if (tick < 20) {
                        val scale = 1.0 - (tick + 1) / 20.0
                        showBox(scaledCorner(a, center, scale), scaledCorner(b, center, scale))
                        if (tick % 4 == 0) sounds.play(center, Sound.BLOCK_PISTON_CONTRACT, volume = 0.45f, pitch = (0.7f + tick * 0.025f))
                        tick++
                        return
                    }

                    val burstTick = tick - 20
                    if (burstTick == 0) {
                        particles.spawn(center, Particle.FLASH, count = 1)
                        sounds.play(center, Sound.BLOCK_BEACON_ACTIVATE, volume = 1.3f, pitch = 1.8f)
                        playerData.radius(center, org.beobma.classWarPlugin.util.TargetType.Enemy, targetRadius, false)
                            .filter { target ->
                                target.entity.boundingBox.overlaps(
                                    BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
                                )
                            }.forEach { target ->
                                target.damage(damage, DamageType.Normal, playerData)
                                particles.spawn(target.entity, Particle.END_ROD, count = 3, spread = 0.3, speed = 0.05)
                            }
                    }
                    if (burstTick % 2 == 0) {
                        burstParticles(center, (burstTick + 1) * targetRadius / 10.0)
                    }
                    tick++
                    if (burstTick >= 9) cancel()
                }
            }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
            return true
        }

        override fun isUseSuccess(): Boolean {
            if (first != null && second != null) return true
            player.sendMiniMessage("<red><bold>[!] 먼저 두 좌표를 지정해야 합니다.")
            return false
        }
    }
}
