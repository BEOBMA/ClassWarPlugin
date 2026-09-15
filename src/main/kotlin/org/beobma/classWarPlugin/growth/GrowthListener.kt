package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.info.Info
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.persistence.PersistentDataType

class GrowthListener : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun knockback(event: com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent) {
        val runtime = Info.game?.growth ?: return
        val source = event.hitBy as? Player ?: return
        val data = runtime.participants().firstOrNull { it.uniqueId == source.uniqueId } ?: return
        event.knockback = GrowthScaling.knockback(data, event.knockback)
    }
    @EventHandler
    fun click(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        // A UI token cannot enter containers, crafting slots, cursors or another player's inventory.
        if (GrowthControls.isToken(event.currentItem) || GrowthControls.isToken(event.cursor) ||
            (event.hotbarButton in 0..8 && GrowthControls.isToken(player.inventory.getItem(event.hotbarButton))) ||
            (event.click == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND && GrowthControls.isToken(player.inventory.itemInOffHand))) {
            event.isCancelled = true
            if (event.isRightClick && GrowthControls.isToken(event.currentItem)) GrowthControls.useToken(player, event.currentItem)
            return
        }
        val menu = event.view.topInventory.holder as? GrowthMenu ?: return
        event.isCancelled = true
        org.bukkit.Bukkit.getScheduler().runTask(org.beobma.classWarPlugin.ClassWarPlugin.instance, Runnable {
            if (player.isOnline && player.openInventory.topInventory.holder === menu)
                GrowthMenu.click(player, menu, event.rawSlot, event.isShiftClick, event.isRightClick)
        })
    }
    @EventHandler
    fun drag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is GrowthMenu || GrowthControls.isToken(event.oldCursor)) event.isCancelled = true
    }
    @EventHandler(ignoreCancelled = true)
    fun drop(event: org.bukkit.event.player.PlayerDropItemEvent) {
        if (GrowthControls.isToken(event.itemDrop.itemStack)) event.isCancelled = true
    }
    @EventHandler
    fun death(event: org.bukkit.event.entity.PlayerDeathEvent) {
        event.drops.removeIf(GrowthControls::isToken)
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun experience(event: org.bukkit.event.player.PlayerExpChangeEvent) {
        if (Info.game?.growth?.players?.containsKey(event.player.uniqueId) == true) event.amount = 0
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun pickup(event: EntityPickupItemEvent) {
        val runtime = Info.game?.growth ?: return
        if (!event.item.persistentDataContainer.has(runtime.entityKey)) return
        event.isCancelled = true
        (event.entity as? Player)?.let { runtime.claim(event.item, it) }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun damage(event: EntityDamageByEntityEvent) {
        val runtime = Info.game?.growth ?: return
        val attacker = (event.damager as? Projectile)?.shooter as? org.bukkit.entity.Entity ?: event.damager
        if (attacker.uniqueId !in runtime.mobs && event.entity.uniqueId !in runtime.mobs) return
        if (runtime.game.isPaused || runtime.game.phase != org.beobma.classWarPlugin.game.GamePhase.RUNNING) {
            event.isCancelled = true; return
        }
        // Outsiders cannot farm or interfere with match-owned creatures.
        if (attacker is Player && attacker.uniqueId !in runtime.players) event.isCancelled = true
        if (event.entity is Player && event.entity.uniqueId !in runtime.players) event.isCancelled = true
        if (!event.isCancelled && attacker.uniqueId in runtime.mobs && event.entity is Player)
            runtime.reduceMobDamage(event.entity as Player, event)
    }
    @EventHandler(ignoreCancelled = true)
    fun interact(event: PlayerInteractEntityEvent) {
        if (Info.game?.growth?.mobs?.containsKey(event.rightClicked.uniqueId) == true) event.isCancelled = true
    }
    @EventHandler(ignoreCancelled = true)
    fun transform(event: EntityTransformEvent) {
        if (Info.game?.growth?.mobs?.containsKey(event.entity.uniqueId) == true) event.isCancelled = true
    }
    @EventHandler(ignoreCancelled = true)
    fun breed(event: EntityBreedEvent) {
        val mobs = Info.game?.growth?.mobs ?: return
        if (event.father.uniqueId in mobs || event.mother.uniqueId in mobs) event.isCancelled = true
    }
    @EventHandler
    fun chunkLoad(event: ChunkLoadEvent) {
        val key = org.bukkit.NamespacedKey(org.beobma.classWarPlugin.ClassWarPlugin.instance, "growth-entity")
        val runtime = Info.game?.growth
        event.chunk.entities.filter { it.persistentDataContainer.has(key, PersistentDataType.STRING) &&
            (runtime == null || !runtime.ownsEntity(it.uniqueId)) }.forEach { it.remove() }
    }
}
