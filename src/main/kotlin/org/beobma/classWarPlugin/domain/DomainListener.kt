package org.beobma.classWarPlugin.domain

import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.*
import kotlin.math.abs

/** Enforce server-authoritative movement, including rotation and plugin/pearl teleports. */
class DomainListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun move(event: PlayerMoveEvent) {
        if (DomainManager.sessions.any { it.relocating == event.player.uniqueId }) return
        val id = event.player.uniqueId
        if (DomainManager.isLocked(id)) { event.to = event.from; return }
        if (DomainManager.blocksCrossing(id, event.from, event.to)) { event.to = event.from; return }
        if (DomainManager.isDistorted(id)) {
            val to = event.to.clone()
            val delta = to.toVector().subtract(event.from.toVector())
            if (delta.lengthSquared() > 0.0009) to.set(event.from.x + delta.x * 0.03 / delta.length(),
                event.from.y + delta.y * 0.03 / delta.length(), event.from.z + delta.z * 0.03 / delta.length())
            val yawDelta = ((to.yaw - event.from.yaw + 540f) % 360f) - 180f
            to.yaw = event.from.yaw + yawDelta.coerceIn(-1f, 1f)
            to.pitch = event.from.pitch + (to.pitch - event.from.pitch).coerceIn(-1f, 1f)
            event.to = to
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun teleport(event: PlayerTeleportEvent) {
        if (DomainManager.sessions.any { it.relocating == event.player.uniqueId }) return
        if (DomainManager.isLocked(event.player.uniqueId) || DomainManager.blocksCrossing(event.player.uniqueId, event.from, event.to)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.LOWEST) fun interact(e: PlayerInteractEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun interactEntity(e: PlayerInteractEntityEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun swap(e: PlayerSwapHandItemsEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun drop(e: PlayerDropItemEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun held(e: PlayerItemHeldEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun sneak(e: PlayerToggleSneakEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun sprint(e: PlayerToggleSprintEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun flight(e: PlayerToggleFlightEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun inventory(e: InventoryClickEvent) { if (DomainManager.isLocked(e.whoClicked.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun drag(e: InventoryDragEvent) { if (DomainManager.isLocked(e.whoClicked.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun consume(e: PlayerItemConsumeEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun shoot(e: EntityShootBowEvent) { if (DomainManager.isLocked(e.entity.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun attack(e: EntityDamageByEntityEvent) {
        val player = e.damager as? Player ?: return
        if (DomainManager.isLocked(player.uniqueId) || (DomainManager.isDistorted(player.uniqueId) && player.attackCooldown < 0.99f)) e.isCancelled = true
    }
    @EventHandler(priority = EventPriority.LOWEST) fun jump(e: com.destroystokyo.paper.event.player.PlayerJumpEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun animate(e: PlayerAnimationEvent) { if (DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun bucket(e: PlayerBucketEmptyEvent) { if (protected(e.block) || DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST) fun bucketFill(e: PlayerBucketFillEvent) { if (protected(e.block) || DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler fun death(e: PlayerDeathEvent) { DomainManager.sessions.toList().forEach { it.removePlayer(e.player.uniqueId) } }
    @EventHandler fun quit(e: PlayerQuitEvent) { DomainManager.sessions.toList().forEach { it.removePlayer(e.player.uniqueId) } }

    private fun protected(block: org.bukkit.block.Block) = DomainManager.sessions.any {
        block.world == it.center.world && abs(block.x - it.center.blockX) <= it.definition.radius + 1 &&
            abs(block.z - it.center.blockZ) <= it.definition.radius + 1 &&
            block.y in (it.center.blockY - 2)..(it.center.blockY + it.definition.radius + 1)
    }
    @EventHandler(ignoreCancelled = true) fun breakBlock(e: BlockBreakEvent) { if (protected(e.block) || DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(ignoreCancelled = true) fun place(e: BlockPlaceEvent) { if (protected(e.block) || DomainManager.isLocked(e.player.uniqueId)) e.isCancelled = true }
    @EventHandler(ignoreCancelled = true) fun explode(e: EntityExplodeEvent) { e.blockList().removeIf(::protected) }
    @EventHandler(ignoreCancelled = true) fun blockExplode(e: BlockExplodeEvent) { e.blockList().removeIf(::protected) }
    @EventHandler(ignoreCancelled = true) fun fluid(e: BlockFromToEvent) { if (protected(e.block) || protected(e.toBlock)) e.isCancelled = true }
    @EventHandler(ignoreCancelled = true) fun piston(e: BlockPistonExtendEvent) { if (protected(e.block) || e.blocks.any { protected(it) || protected(it.getRelative(e.direction)) }) e.isCancelled = true }
    @EventHandler(ignoreCancelled = true) fun retract(e: BlockPistonRetractEvent) { if (protected(e.block) || e.blocks.any(::protected)) e.isCancelled = true }
    @EventHandler(ignoreCancelled = true) fun entityBlock(e: EntityChangeBlockEvent) { if (protected(e.block)) e.isCancelled = true }
    @EventHandler(ignoreCancelled = true) fun physics(e: BlockPhysicsEvent) { if (protected(e.block)) e.isCancelled = true }
    @EventHandler(ignoreCancelled = true) fun burn(e: BlockBurnEvent) { if (protected(e.block)) e.isCancelled = true }
}
