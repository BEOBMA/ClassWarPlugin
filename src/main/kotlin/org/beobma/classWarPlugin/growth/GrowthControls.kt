package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.manager.GameManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** The token is a reusable, owner-bound UI key, not one consumable per stat point. */
object GrowthControls {
    private val key get() = NamespacedKey(ClassWarPlugin.instance, "growth-stat-token")
    fun isToken(item: ItemStack?) = item?.itemMeta?.persistentDataContainer?.has(key) == true
    fun firstFreeSlot(empty: (Int) -> Boolean): Int? = (0..35).firstOrNull(empty)

    fun sync(player: Player, state: GrowthPlayerState, runtime: GrowthModeRuntime): Boolean {
        val inventory = player.inventory
        val owned = "${runtime.sessionId}:${player.uniqueId}"
        var kept = false
        val token = GrowthMenu.item(Material.NETHER_STAR, "<gold>레벨업 · 미배분 ${state.points}점",
            listOf("<yellow>우클릭: 스탯 배분", "<gray>포인트를 모두 사용하면 사라집니다.", "<gray>장비 보관함: Shift + F")).apply {
            itemMeta = itemMeta.apply { persistentDataContainer.set(key, PersistentDataType.STRING, owned) }
        }
        for (slot in 0 until inventory.size) {
            val current = inventory.getItem(slot)
            if (!isToken(current)) continue
            if (!kept && state.points > 0 && slot in 0..35 &&
                current!!.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING) == owned) {
                if (current != token) inventory.setItem(slot, token)
                kept = true
            } else inventory.setItem(slot, null)
        }
        if (state.points <= 0 || kept) return true
        val slot = firstFreeSlot { inventory.getItem(it)?.type?.isAir != false } ?: return false
        inventory.setItem(slot, token)
        return true
    }

    fun remove(player: Player) {
        for (slot in 0 until player.inventory.size) if (isToken(player.inventory.getItem(slot))) player.inventory.setItem(slot, null)
        if (isToken(player.itemOnCursor)) player.setItemOnCursor(null)
    }

    fun useToken(player: Player, item: ItemStack?, open: Boolean = true): Boolean {
        if (!isToken(item)) return false
        val runtime = GameManager.findGameForPlayer(player)?.growth
        if (open && runtime != null && item!!.itemMeta.persistentDataContainer.get(key, PersistentDataType.STRING) ==
            "${runtime.sessionId}:${player.uniqueId}" && (runtime.players[player.uniqueId]?.points ?: 0) > 0) {
            openMenu(player, runtime, "stats")
        }
        return true // Always suppress normal item/weapon interactions, including stale keys.
    }

    fun equipmentKey(player: Player): Boolean {
        val runtime = GameManager.findGameForPlayer(player)?.growth ?: return false
        if (!player.isSneaking || player.uniqueId !in runtime.players) return false
        openMenu(player, runtime, "items")
        return true
    }

    fun openMenu(player: Player, runtime: GrowthModeRuntime, kind: String) {
        Bukkit.getScheduler().runTask(ClassWarPlugin.instance, Runnable {
            if (player.isOnline && GameManager.findGameForPlayer(player)?.growth === runtime &&
                runtime.game.phase == GamePhase.RUNNING && !runtime.game.isPaused && !player.isDead &&
                runtime.participants().any { it.uniqueId == player.uniqueId && !it.entityStatus.isDead }) GrowthMenu.open(player, kind)
        })
    }
}
