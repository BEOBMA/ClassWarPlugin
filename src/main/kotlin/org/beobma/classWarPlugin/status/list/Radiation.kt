package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.Player
import java.util.UUID

class Radiation : StatusAbnormality() {
    override val name = Keyword.Radiation.string
    override val description = listOf(Keyword.Radiation.description ?: "")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null

    private var glowApplied = false

    override fun onPowerChanged() {
        super.onPowerChanged()
        applyGlow()
    }

    override fun onDurationChanged() {
        super.onDurationChanged()
        applyGlow()
    }

    override fun onRemoveStatusAbnormality() {
        val player = entity as? Player
        if (glowApplied && player != null) {
            val remaining = (activeCounts[player.uniqueId] ?: 1) - 1
            if (remaining <= 0) {
                activeCounts.remove(player.uniqueId)
                val wasGlowing = originalGlowing.remove(player.uniqueId) ?: false
                if (player.isOnline) player.isGlowing = wasGlowing
            } else {
                activeCounts[player.uniqueId] = remaining
            }
        }
        glowApplied = false
        super.onRemoveStatusAbnormality()
    }

    private fun applyGlow() {
        if (power <= 0 || duration?.let { it <= 0 } == true) return
        val player = entity as? Player ?: return
        if (!glowApplied) {
            originalGlowing.putIfAbsent(player.uniqueId, player.isGlowing)
            activeCounts[player.uniqueId] = (activeCounts[player.uniqueId] ?: 0) + 1
            glowApplied = true
        }
        player.isGlowing = true
    }

    companion object {
        private val originalGlowing = mutableMapOf<UUID, Boolean>()
        private val activeCounts = mutableMapOf<UUID, Int>()
    }
}
