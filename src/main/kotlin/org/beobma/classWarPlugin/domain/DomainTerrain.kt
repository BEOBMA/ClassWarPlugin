package org.beobma.classWarPlugin.domain

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockState
import org.bukkit.block.data.BlockData
import java.util.UUID

/** One original snapshot per world cell. Removing any owner reveals surviving domains, never stale walls. */
internal object DomainTerrain {
    private data class Key(val world: UUID, val x: Int, val y: Int, val z: Int)
    private data class Cell(val original: BlockState, val layers: LinkedHashMap<DomainSession, BlockData> = linkedMapOf())
    private val cells = linkedMapOf<Key, Cell>()
    private val pending = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Cell, Boolean>())
    fun retryBlocked() { pending.toList().forEach(::paint) }
    private fun key(at: Location) = Key(at.world.uid,at.blockX,at.blockY,at.blockZ)
    fun original(at: Location): BlockState = cells[key(at)]?.original ?: at.block.state
    fun put(owner: DomainSession, at: Location, data: BlockData) {
        val cell = cells.getOrPut(key(at)) { Cell(at.block.state) }
        cell.layers[owner] = data.clone()
        paint(cell)
    }
    fun hasOther(owner: DomainSession, state: BlockState) = cells[key(state.location)]?.layers?.keys?.any { it !== owner } == true
    fun release(owner: DomainSession, state: BlockState): Boolean {
        val key = key(state.location)
        val cell = cells[key] ?: return state.update(true,false)
        cell.layers.remove(owner)
        if (cell.layers.isNotEmpty()) { paint(cell); return true }
        if (!cell.original.update(true,false)) return false
        pending.remove(cell)
        cells.remove(key)
        return true
    }
    fun repaint() { cells.values.toList().forEach(::paint) }
    private fun paint(cell: Cell) {
        val at = cell.original.location
        val active = cell.layers.keys.filter { !it.isRestoringTerrain }
        val interior = active.filter { s -> DomainShell.isInterior(s.definition.radius,
            at.blockX-s.center.blockX,at.blockY-s.center.blockY,at.blockZ-s.center.blockZ) }
        // An overlapping interior wins over another domain's shell. Clash architecture is neutral.
        val owner = interior.lastOrNull() ?: active.lastOrNull() ?: cell.layers.keys.lastOrNull() ?: return
        val desired = if (owner.clashed && interior.isNotEmpty()) {
            if (interior.any { at.blockY == it.center.blockY-1 }) Material.STONE.createBlockData()
            else Material.AIR.createBlockData()
        } else cell.layers.getValue(owner)
        if (at.block.blockData == desired) { pending.remove(cell); return }
        if (desired.material.isSolid) {
            // Newly restored inner boundary must never materialize inside a player.
            val players = at.world.players.filter { !it.isDead && it.gameMode != org.bukkit.GameMode.SPECTATOR &&
                DomainRestoreSafety.overlaps(it.boundingBox,at.blockX,at.blockY,at.blockZ) }
            players.forEach { player ->
                DomainRestoreSafety.findDestination(player, owner.center, at.world.worldBorder.center,
                    at.world.worldBorder.size) { null }?.let { owner.relocate(player,it); player.fallDistance=0f }
            }
            if (players.any { DomainRestoreSafety.overlaps(it.boundingBox,at.blockX,at.blockY,at.blockZ) }) { pending += cell; return }
        }
        at.block.setBlockData(desired,false)
        pending.remove(cell)
    }
}
