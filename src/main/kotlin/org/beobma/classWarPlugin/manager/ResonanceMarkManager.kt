package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.handler.ResonanceResourceUser
import org.beobma.classWarPlugin.keyword.StatusIcon
import org.beobma.classWarPlugin.status.list.Aftermath
import org.beobma.classWarPlugin.status.list.Resonance
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.*

/** One central display per target; camera-facing particles are sent only to nearby visible viewers. */
object ResonanceMarkManager {
    private data class Mark(val data: EntityData, val display: TextDisplay, var text: String = "")
    private val marks = mutableMapOf<UUID, Mark>()
    private var task: BukkitTask? = null
    private val empty = Particle.DustOptions(Color.fromRGB(65, 51, 76), 0.30f)
    private val full = Particle.DustOptions(Color.fromRGB(164, 91, 212), 0.42f)
    private val progress = Particle.DustOptions(Color.fromRGB(235, 184, 68), 0.42f)
    private val divider = Particle.DustOptions(Color.fromRGB(231, 213, 247), 0.36f)

    /** 30 aftermath fills one third; 3 resonance fills the whole circle. */
    fun filledSteps(resonance: Int, aftermath: Int): Int =
        (resonance.coerceIn(0, 3) * 30 + aftermath.coerceIn(0, 29)).coerceAtMost(90)

    fun update(data: EntityData) {
        val active = data.statusAbnormalitys.any { (it is Aftermath || it is Resonance) && it.power > 0 }
        if (!active || !data.entity.isValid || data.entity.isDead) {
            marks.remove(data.entity.uniqueId)?.display?.remove()
            return
        }
        marks.getOrPut(data.entity.uniqueId) {
            val display = data.entity.world.spawn(data.entity.location, TextDisplay::class.java).apply {
                isPersistent = false
                isVisibleByDefault = false
                billboard = Display.Billboard.CENTER
                backgroundColor = Color.fromARGB(0, 0, 0, 0)
                isSeeThrough = false
                isShadowed = true
                teleportDuration = 2
                transformation = transformation.apply { scale.set(2f) }
            }
            Mark(data, display)
        }
        if (task == null) task = ClassWarPlugin.instance.server.scheduler.runTaskTimer(
            ClassWarPlugin.instance, Runnable { tick() }, 1L, 4L)
    }

    private fun tick() {
        // Evaluate once per viewer each tick, so losing/swapping an ability revokes visibility.
        val eligibleViewers = mutableMapOf<UUID, Boolean>()
        val iterator = marks.values.iterator()
        while (iterator.hasNext()) {
            val mark = iterator.next()
            val entity = mark.data.entity
            val statuses = mark.data.statusAbnormalitys
            val resonance = statuses.filterIsInstance<Resonance>().maxOfOrNull { it.power } ?: 0
            val aftermath = statuses.filterIsInstance<Aftermath>().maxOfOrNull { it.power } ?: 0
            if (!entity.isValid || entity.isDead || (entity is Player && !entity.isOnline) ||
                resonance + aftermath <= 0 || !mark.display.isValid) {
                mark.display.remove()
                iterator.remove()
                continue
            }
            val center = entity.location.clone().apply { y = entity.boundingBox.maxY + 0.95; yaw = 0f; pitch = 0f }
            mark.display.teleport(center.clone().subtract(0.0, 0.20, 0.0))
            val text = StatusIcon.markup("ResonanceMark")
            if (mark.text != text) {
                mark.display.text(UtilManager.miniMessage.deserialize(text))
                mark.text = text
            }
            val filled = filledSteps(resonance, aftermath)
            for (viewer in entity.world.players) {
                val visible = viewer.uniqueId != entity.uniqueId &&
                    eligibleViewers.getOrPut(viewer.uniqueId) { canViewMarks(viewer) } &&
                    viewer.location.distanceSquared(center) <= 24.0 * 24.0 &&
                    (entity !is Player || viewer.canSee(entity)) && viewer.hasLineOfSight(entity)
                if (!visible) {
                    viewer.hideEntity(ClassWarPlugin.instance, mark.display)
                    continue
                }
                viewer.showEntity(ClassWarPlugin.instance, mark.display)
                val normal = viewer.eyeLocation.toVector().subtract(center.toVector())
                if (normal.lengthSquared() < 0.001) continue
                normal.normalize()
                var right = Vector(0.0, 1.0, 0.0).crossProduct(normal)
                if (right.lengthSquared() < 0.001) right = Vector(1.0, 0.0, 0.0)
                right.normalize()
                val up = normal.clone().crossProduct(right).normalize()
                fun dot(angle: Double, radius: Double, color: Particle.DustOptions) {
                    val position = center.clone().add(right.clone().multiply(sin(angle) * radius))
                        .add(up.clone().multiply(cos(angle) * radius))
                    viewer.spawnParticle(Particle.DUST, position, 1, 0.0, 0.0, 0.0, 0.0, color)
                }
                repeat(90) { index ->
                    val angle = (index + 0.5) * 2.0 * PI / 90.0
                    val color = if (index >= filled) empty else if (index < resonance * 30) full else progress
                    dot(angle, 0.60, color)
                }
                repeat(3) { boundary ->
                    repeat(5) { step -> dot(boundary * 2.0 * PI / 3.0, 0.47 + step * 0.065, divider) }
                }
            }
        }
        if (marks.isEmpty()) { task?.cancel(); task = null }
    }

    private fun canViewMarks(viewer: Player): Boolean {
        val data = GameManager.findGameForPlayer(viewer)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.firstOrNull { it.uniqueId == viewer.uniqueId } ?: return false
        if (data.entityStatus.isDead) return false
        return AbilityTree.handlers(data.gameClasses, ResonanceResourceUser::class.java)
            .any { !it.scope.suspended && it.scope.started }
    }

    fun shutdown() {
        task?.cancel()
        task = null
        marks.values.forEach { it.display.remove() }
        marks.clear()
    }
}
