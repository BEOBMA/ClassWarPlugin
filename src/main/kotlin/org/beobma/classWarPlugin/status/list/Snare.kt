package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.bukkit.event.player.PlayerMoveEvent

class Snare : StatusAbnormality(), StatusPlayerMoveHandler {
    override val name = Keyword.Snare.string
    override val description = listOf(Keyword.Snare.description ?: "")
    override val canRemove = true
    override var power = 1
    override var maxPower: Int? = 1
    override var duration: Int? = null
    override val showPower = false
    override val showMaxPower = false

    override fun onPlayerMove(event: PlayerMoveEvent, playerData: PlayerData) {
        val from = event.from
        val to = event.to
        if (from.x == to.x && from.y == to.y && from.z == to.z) return
        event.to = from.clone().apply {
            yaw = to.yaw
            pitch = to.pitch
        }
    }
}
