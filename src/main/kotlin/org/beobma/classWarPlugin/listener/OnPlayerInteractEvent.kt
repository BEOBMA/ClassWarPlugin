package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class OnPlayerInteractEvent : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val isTraining = PlayerTagManager.hasTag(player, "isTraining")
        if (!isGaming() && !isTraining) return

        val clickedItem = event.item ?: return
        val skillId = getSkillId(clickedItem, player.uniqueId) ?: return
        val currentGame = findGameForPlayer(player) ?: return
        val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.player.uniqueId == player.uniqueId } ?: return
        val skill = playerData.gameClass?.skills?.find { it.id == skillId } ?: return

        event.isCancelled = true
        playerData.use(skill, clickedItem)
    }
}
