package org.beobma.classWarPlugin.event

import org.beobma.classWarPlugin.skill.SkillContext
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class PlayerSkillUseEvent(
    val context: SkillContext,
) : Event(), Cancellable {
    val playerData get() = context.playerData
    val skill get() = context.skill
    val clickedItem: ItemStack get() = context.clickedItem
    val isToggle: Boolean get() = context.isToggle

    override fun isCancelled(): Boolean {
        return context.isCancelled
    }

    override fun setCancelled(cancel: Boolean) {
        context.isCancelled = cancel
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
