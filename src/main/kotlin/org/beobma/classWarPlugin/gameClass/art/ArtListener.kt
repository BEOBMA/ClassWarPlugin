package org.beobma.classWarPlugin.gameClass.art

import org.bukkit.event.*
import org.bukkit.event.player.*
import org.bukkit.event.inventory.*
import org.bukkit.event.block.*

/** The painter's WASD is polled by its camera; other actions and cartoon playback remain locked. */
class ArtListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun environmental(e: org.bukkit.event.entity.EntityDamageEvent) {
        // Entity attacks already went through the confirmed-damage hook.
        if (e !is org.bukkit.event.entity.EntityDamageByEntityEvent && e.finalDamage > 0.0)
            ArtistRuntime.onEnvironmentalDamage(e.entity)
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun gesture(event: PlayerAnimationEvent) { CartoonRuntime.recordGesture(event.player.uniqueId) }
    @EventHandler(priority = EventPriority.LOWEST)
    fun interact(e: PlayerInteractEvent) { if (ArtistRuntime.isPainting(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun entity(e: PlayerInteractEntityEvent) {
        if (ArtistRuntime.isPainting(e.player.uniqueId) || "cw-cartoon-frame" in e.rightClicked.scoreboardTags) e.isCancelled = true
    }
    @EventHandler(priority = EventPriority.LOWEST)
    fun swap(e: PlayerSwapHandItemsEvent) { if (ArtistRuntime.isPainting(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun drop(e: PlayerDropItemEvent) { if (ArtistRuntime.isPainting(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun held(e: PlayerItemHeldEvent) { if (ArtistRuntime.isPainting(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun consume(e: PlayerItemConsumeEvent) { if (ArtistRuntime.isPainting(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun inventory(e: InventoryClickEvent) { if (ArtistRuntime.isPainting(e.whoClicked.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun drag(e: InventoryDragEvent) { if (ArtistRuntime.isPainting(e.whoClicked.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun breaking(e: BlockBreakEvent) { if (ArtistRuntime.isPainting(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun placing(e: BlockPlaceEvent) { if (ArtistRuntime.isPainting(e.player.uniqueId)) e.isCancelled = true }
}
