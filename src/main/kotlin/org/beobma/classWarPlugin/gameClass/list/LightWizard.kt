package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetBlock
import org.beobma.classWarPlugin.manager.SkillManager.getTargetCandidates
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Brightness
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.*
import org.bukkit.util.Vector
import org.bukkit.entity.BlockDisplay
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.ArrayDeque
import kotlin.math.max

// 밸런스 조정 상수
private const val LIGHT_WIZARD_PRISM_COOLDOWN_SECONDS = 1
private const val LIGHT_WIZARD_SPECTRUM_COOLDOWN_SECONDS = 10
private const val LIGHT_WIZARD_PRIMARY_BEAM_DAMAGE = 8.0
private const val LIGHT_WIZARD_SPLIT_BEAM_DAMAGE = 4.0
private const val LIGHT_WIZARD_MIN_BEAM_DAMAGE = 1.0
private const val LIGHT_WIZARD_MAX_PRISMS = 5
private const val LIGHT_WIZARD_PRISM_PULSE_RADIUS = 2.0
private const val LIGHT_WIZARD_PRISM_PULSE_POINTS = 48

class LightWizard : GameClass() {
    override val name = "<gray>프리즘"
    override val rank = Rank.A
    override val classItemMaterial = Material.LIGHT

    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive()
    )

    private data class Prism(val location: Location, val display: BlockDisplay)
    private val prisms = ArrayDeque<Prism>()

    private fun placePrism(): Boolean {
        val trace = player.world.rayTraceBlocks(player.eyeLocation, player.eyeLocation.direction, 10.0) ?: return false
        val hitPosition = trace.hitPosition
        val hitFace = trace.hitBlockFace ?: return false
        val surfaceNormal = hitFace.direction.normalize()
        val scale = 0.72
        val location = hitPosition.toLocation(player.world).add(surfaceNormal.clone().multiply(scale / 2.0 + 0.04))
        if (prisms.size >= LIGHT_WIZARD_MAX_PRISMS) prisms.removeFirst().display.remove()
        val displayLocation = location.clone().add(-scale / 2.0, -scale / 2.0, -scale / 2.0)
        val display = location.world.spawn(displayLocation, BlockDisplay::class.java)
        display.block = Material.AMETHYST_BLOCK.createBlockData()
        display.isPersistent = false
        TemporaryDisplayManager.mark(display, player.uniqueId)
        display.transformation = Transformation(
            Vector3f(0.0f, 0.0f, 0.0f),
            Quaternionf(),
            Vector3f(scale.toFloat(), scale.toFloat(), scale.toFloat()),
            Quaternionf(),
        )
        val prism = Prism(location.clone(), display)
        prisms.addLast(prism)
        playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (prism !in prisms || !display.isValid) {
                    display.remove()
                    cancel()
                    return
                }
                val bob = kotlin.math.sin(tick * 0.15) * 0.035
                tick += 2
                display.teleport(displayLocation.clone().add(surfaceNormal.clone().multiply(bob)))
                if (tick % 8 == 0) particles.spawn(location, Particle.END_ROD, count = 2, spread = 0.32)
            }
        }.runTaskTimer(org.beobma.classWarPlugin.ClassWarPlugin.instance, 0L, 2L))
        particles.spawn(location, Particle.END_ROD, count = 25, spread = 0.25, speed = 0.03)
        sounds.play(location, Sound.BLOCK_GLASS_PLACE, pitch = 1.6f)
        return true
    }

    private data class Beam(val start: Location, val direction: Vector, val depth: Int)

    private fun fireBeams() {
        val queue = ArrayDeque<Beam>()
        queue += Beam(player.eyeLocation.clone(), player.eyeLocation.direction.normalize(), 0)
        val activated = mutableSetOf<Prism>()
        var processed = 0
        while (queue.isNotEmpty() && processed++ < 1 + LIGHT_WIZARD_MAX_PRISMS * 4) {
            val beam = queue.removeFirst()
            val maxDistance = 24.0
            val blockHit = beam.start.world.rayTraceBlocks(beam.start, beam.direction, maxDistance)?.hitPosition
            var distance = blockHit?.distance(beam.start.toVector()) ?: maxDistance
            val prism = prisms
                .filter { it !in activated && it.location.world == beam.start.world }
                .mapNotNull { prism ->
                    val relative = prism.location.toVector().subtract(beam.start.toVector())
                    val projection = relative.dot(beam.direction)
                    if (projection <= 0.15 || projection >= distance) return@mapNotNull null
                    val perpendicular = relative.clone().subtract(beam.direction.clone().multiply(projection)).length()
                    if (perpendicular <= 0.8) prism to projection else null
                }.minByOrNull { it.second }
            if (prism != null) distance = prism.second
            val end = beam.start.clone().add(beam.direction.clone().multiply(distance))
            particles.line(beam.start, end, Particle.END_ROD, spacing = 0.3)
            hitBeamTargets(beam, end)
            if (prism != null) {
                activatePrismCluster(prism.first, beam.depth + 1, queue, activated)
            }
        }
        sounds.play(player, Sound.BLOCK_BEACON_ACTIVATE, pitch = 1.7f)
    }

    private fun hitBeamTargets(beam: Beam, end: Location) {
        val start = beam.start.toVector()
        val finish = end.toVector()
        if (finish.distanceSquared(start) <= 0.0) return
        playerData.getTargetCandidates().filter { it != playerData && it.entityStatus.isSkillTargeting }.forEach { target ->
            if (target is PlayerData && !playerData.isEnemyOf(target)) return@forEach
            if (!HitboxUtil.intersectsSegment(target.entity.boundingBox, start, finish, expansion = 0.25)) return@forEach
            damageWithLight(target, beam.depth)
        }
    }

    private fun activatePrismCluster(
        first: Prism,
        outputDepth: Int,
        beamQueue: ArrayDeque<Beam>,
        activated: MutableSet<Prism>,
    ) {
        val pending = ArrayDeque<Prism>()
        pending += first
        while (pending.isNotEmpty()) {
            val prism = pending.removeFirst()
            if (!prism.display.isValid || !activated.add(prism)) continue

            renderPrismPulse(prism.location)
            hitPrismPulseTargets(prism.location, outputDepth)
            CARDINAL_BEAM_DIRECTIONS.forEach { direction ->
                beamQueue += Beam(prism.location.clone(), direction.clone(), outputDepth)
            }

            prisms.asSequence()
                .filter { nearby ->
                    nearby !in activated && nearby.display.isValid &&
                        nearby.location.world == prism.location.world &&
                        nearby.location.distanceSquared(prism.location) <=
                            LIGHT_WIZARD_PRISM_PULSE_RADIUS * LIGHT_WIZARD_PRISM_PULSE_RADIUS
                }
                .forEach(pending::addLast)
        }
    }

    private fun renderPrismPulse(center: Location) {
        sounds.play(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, pitch = 1.8f)
        particles.spawn(center, Particle.FLASH, count = 1)
        particles.circle(
            center,
            Particle.END_ROD,
            LIGHT_WIZARD_PRISM_PULSE_RADIUS,
            LIGHT_WIZARD_PRISM_PULSE_POINTS,
        )
        particles.spawn(center, Particle.ELECTRIC_SPARK, count = 16, spread = 0.65, speed = 0.055)
    }

    private fun hitPrismPulseTargets(center: Location, depth: Int) {
        playerData.getTargetCandidates()
            .filter { it != playerData && it.entityStatus.isSkillTargeting }
            .forEach { target ->
                if (target is PlayerData && !playerData.isEnemyOf(target)) return@forEach
                if (!HitboxUtil.intersectsSphere(
                        target.entity.boundingBox,
                        center.toVector(),
                        LIGHT_WIZARD_PRISM_PULSE_RADIUS,
                    )
                ) return@forEach
                damageWithLight(target, depth)
            }
    }

    private fun damageWithLight(target: org.beobma.classWarPlugin.entity.EntityData, depth: Int) {
        target.damage(lightDamage(depth), DamageType.Normal, playerData)
        if (depth > 0) {
            target.getOrCreateStatus(playerData) { Brightness() }.applyStatus(powerDelta = 1)
        }
    }

    private fun lightDamage(depth: Int): Double = max(
        LIGHT_WIZARD_MIN_BEAM_DAMAGE,
        if (depth == 0) LIGHT_WIZARD_PRIMARY_BEAM_DAMAGE
        else LIGHT_WIZARD_SPLIT_BEAM_DAMAGE / (1 shl (depth - 1)),
    )

    private inner class RedSkill : Skill() {
        override val name = "<bold>프리즘"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 블럭에 프리즘을 설치한다. (최대 5개)",
            "<gray>최대 개수를 초과하여 설치할 경우 가장 오래된 프리즘을 제거하고 설치한다."
        )
        override val cooldown = LIGHT_WIZARD_PRISM_COOLDOWN_SECONDS

        override fun use() {
            placePrism()
        }

        override fun isUseSuccess(): Boolean {
            if (playerData.shotLaserGetBlock(10.0) != null) return true
            player.sendMiniMessage("<red><bold>[!] 바라보는 블럭이 없습니다.")
            return false
        }
    }

    private inner class OrangeSkill : Skill() {
        override val name = "<bold>분광"
        override val description = listOf(
            "<gray>바라보는 방향으로 빛의 광선을 발사한다.",
            "<gray>광선에 직접 적중한 적은 8의 피해를 입는다.",
            "",
            "<gray>광선이 프리즘에 적중하면 해당 프리즘이 활성화된다.",
            "<gray>활성화 시 지름 4칸의 빛을 방출해 범위 안의 적에게 광선과 동일한 피해를 입힌다.",
            "<gray>빛의 범위 안에 있는 다른 프리즘도 연쇄 활성화된다.",
            "<gray>활성화된 프리즘은 십자 방향으로 빛의 광선을 방출한다.",
            "<gray>프리즘에서 방출된 빛의 광선에 적중한 적은 4의 피해를 입는다.",
            "",
            "<dark_gray>흩뿌려진 광선 또한 또다시 프리즘으로 반사될 수 있다.",
            "<dark_gray>단, 빛의 광선은 같은 프리즘에 1번만 반사될 수 있다.",
            "<dark_gray>적은 처음 발사한 광선을 포함하여 여러 광선에 적중될 수 있다.",
            "<dark_gray>적중한 모든 적은 4의 피해를 입으나, 반사된 횟수에 따라 피해량이 절반으로 감소한다. (최소 1)"
        )
        override val cooldown = LIGHT_WIZARD_SPECTRUM_COOLDOWN_SECONDS

        override fun use() {
            fireBeams()
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>루멘"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>프리즘에서 방출된 빛의 광선에 적중한 적에게 {keyword:Brightness}를 1 부여한다."
        )
    }

    companion object {
        private val CARDINAL_BEAM_DIRECTIONS = listOf(
            Vector(1, 0, 0),
            Vector(-1, 0, 0),
            Vector(0, 0, 1),
            Vector(0, 0, -1),
        )
    }
}
