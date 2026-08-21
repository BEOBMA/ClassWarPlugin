package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.SkillManager.isBoundSkillItem
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerDropItemEvent

class OnSkillItemProtectionEvent : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onDropSkillItem(event: PlayerDropItemEvent) {
        if (isBoundSkillItem(event.itemDrop.itemStack)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onMoveSkillItem(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        val clickedTop = event.rawSlot in 0 until topInventory.size
        val cursorHasSkill = isBoundSkillItem(event.cursor)
        val hotbarHasSkill = event.hotbarButton >= 0 &&
            isBoundSkillItem(event.whoClicked.inventory.getItem(event.hotbarButton))

        if ((event.click == ClickType.DROP || event.click == ClickType.CONTROL_DROP) &&
            isBoundSkillItem(event.currentItem)
        ) {
            event.isCancelled = true
            return
        }

        if (clickedTop && (cursorHasSkill || hotbarHasSkill)) {
            event.isCancelled = true
            return
        }

        val isPlayerInventoryView = topInventory.type == InventoryType.CRAFTING ||
            topInventory.type == InventoryType.CREATIVE
        if (!isPlayerInventoryView && event.isShiftClick && isBoundSkillItem(event.currentItem)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDragSkillItem(event: InventoryDragEvent) {
        if (!event.newItems.values.any(::isBoundSkillItem)) return
        if (event.rawSlots.any { it < event.view.topInventory.size }) event.isCancelled = true
    }
}
