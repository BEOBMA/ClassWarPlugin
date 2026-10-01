package org.beobma.classWarPlugin.gameClass.streamer

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.AbilityScope
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack

/** One escrow per target, including overlapping donations from different streamers. */
internal class BroadcastEscrow(private val target: Player) : StatusAbnormality(), Listener {
    override val name = "<red>후원 압수"
    override val description = listOf("후원으로 사라진 장비가 잠시 후 돌아온다.")
    override val canRemove = true
    override val showPower = false
    override val growsWithStats = false
    private val saved = linkedMapOf<Int, ItemStack>()
    private var closed = false

    private fun capture(all: Boolean) {
        target.closeInventory() // Bukkit returns a cursor stack before the snapshot.
        for (slot in 0 until target.inventory.size) {
            val stack = target.inventory.getItem(slot) ?: continue
            if (slot in saved || stack.type.isAir) continue
            if (!all && getWeaponClassId(stack) == null) continue
            saved[slot] = stack.clone()
            target.inventory.setItem(slot, null)
        }
        // Repeated donations extend rather than overwrite the escrow; there is only one restoration.
        updatePower(1)
        updateDuration(maxOf(duration ?: 0, if (all) 5 else 3))
    }

    override fun onRemoveStatusAbnormality() {
        if (closed) return
        closed = true
        HandlerList.unregisterAll(this)
        saved.forEach { (slot, item) ->
            val displaced = target.inventory.getItem(slot)
            target.inventory.setItem(slot, item)
            if (displaced != null && !displaced.type.isAir) {
                // Other abilities can grant items while the inventory is locked. Never overwrite them.
                target.inventory.addItem(displaced).values.forEach { target.world.dropItemNaturally(target.location, it) }
            }
        }
        saved.clear()
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun click(e: InventoryClickEvent) { if (e.whoClicked.uniqueId == target.uniqueId) e.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun drag(e: InventoryDragEvent) { if (e.whoClicked.uniqueId == target.uniqueId) e.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun pickup(e: EntityPickupItemEvent) { if (e.entity.uniqueId == target.uniqueId) e.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun swap(e: PlayerSwapHandItemsEvent) { if (e.player.uniqueId == target.uniqueId) e.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun drop(e: PlayerDropItemEvent) { if (e.player.uniqueId == target.uniqueId) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun quit(e: PlayerQuitEvent) { if (e.player.uniqueId == target.uniqueId) cleanupFromManager() }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun death(e: PlayerDeathEvent) {
        if (e.entity.uniqueId != target.uniqueId) return
        if (!e.keepInventory) { e.drops.addAll(saved.values.map { it.clone() }); saved.clear() }
        cleanupFromManager()
    }

    companion object {
        fun hide(data: EntityData, scope: AbilityScope, all: Boolean) {
            val target = data.entity as? Player ?: return
            val escrow = data.statusAbnormalitys.filterIsInstance<BroadcastEscrow>().firstOrNull()
                ?: BroadcastEscrow(target).also {
                    data.addStatus(it, scope.playerData)
                    Bukkit.getPluginManager().registerEvents(it, ClassWarPlugin.instance)
                    scope.resources.own(isAlive = { !it.closed }) { it.cleanupFromManager() }
                }
            if (!escrow.applicationBlocked) escrow.capture(all)
        }
    }
}
