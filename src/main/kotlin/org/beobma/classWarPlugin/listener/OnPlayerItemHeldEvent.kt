package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent

class OnPlayerItemHeldEvent : Listener {

    @EventHandler
    fun onHotbarChange(event: PlayerItemHeldEvent) {
        val player = event.player
        val next = event.newSlot
        val isTraining = PlayerTagManager.hasTag(player, "isTraining")

        if (!isGaming() && !isTraining) return
        if (next == 0) return

        val currentGame = findGameForPlayer(player) ?: return
        val playerData = currentGame.playerDatas.find { it.player == player } ?: return
        val gameClass = playerData.gameClass ?: return
        val skill = gameClass.skills.getOrNull(next - 1)
        val clickedItem = player.inventory.getItem(next)
        event.isCancelled = true

        if (skill == null || clickedItem == null) return

        playerData.use(skill, clickedItem)
    }
}
