package org.beobma.classWarPlugin.event

import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class PlayerSkillDamageByPlayerEvent(var damage: Double, val damageType: DamageType, val damager: PlayerData, val entity: PlayerData) : Event(), Cancellable {
    private var isCancelled = false

    override fun isCancelled(): Boolean {
        return isCancelled
    }

    override fun setCancelled(cancel: Boolean) {
        isCancelled = cancel
    }

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
}