package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.Pioneer
import org.bukkit.Location

/** Collect actual targeting geometry during a successful cast, without revealing it to other players. */
object AbilityForecast {
    private val pending = ThreadLocal<MutableList<Location>?>()

    fun cast(caster: PlayerData, action: () -> Boolean): Boolean {
        val previous = pending.get()
        val points = mutableListOf<Location>()
        pending.set(points)
        try {
            val success = action()
            if (success && points.isNotEmpty()) show(caster, points)
            return success
        } finally { pending.set(previous) }
    }

    fun line(caster: PlayerData, start: Location, end: Location, independent: Boolean = false) {
        if (start.world != end.world) return
        val distance = start.distance(end)
        val count = (distance * 2).toInt().coerceIn(1, 96)
        val delta = end.toVector().subtract(start.toVector())
        val points = (0..count).map { start.clone().add(delta.clone().multiply(it.toDouble() / count)) }
        val collecting = pending.get()
        if (collecting != null) {
            collecting.addAll(points.take((384 - collecting.size).coerceAtLeast(0)))
        } else if (independent) show(caster, points)
    }

    fun circle(caster: PlayerData, center: Location, radius: Double) {
        if (pending.get() == null) return
        for (angle in 0 until 360 step 15) {
            val a = Math.toRadians(angle.toDouble())
            val b = Math.toRadians((angle + 15).toDouble())
            line(caster, center.clone().add(kotlin.math.cos(a) * radius, 0.0, kotlin.math.sin(a) * radius),
                center.clone().add(kotlin.math.cos(b) * radius, 0.0, kotlin.math.sin(b) * radius))
        }
    }

    private fun show(caster: PlayerData, points: List<Location>) {
        caster.game.playerDatas.filterIsInstance<PlayerData>().filter { it != caster }.forEach { observer ->
            AbilityTree.nodes(observer.gameClasses, activeOnly = true).filterIsInstance<Pioneer>()
                .forEach { ability -> AbilityExecution.with(ability.abilityScope) { ability.preview(caster, points) } }
        }
    }
}
