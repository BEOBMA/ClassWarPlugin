package org.beobma.classWarPlugin.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.CooperativeAction
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent

/** 공동 모드에서 핫바 조작 담당자가 아닌 참가자의 슬롯 변경을 막는다. */
class OnPlayerItemHeldEvent : Listener {
    private val miniMessage = MiniMessage.miniMessage()

    @EventHandler(ignoreCancelled = true)
    fun onHeldItemChange(event: PlayerItemHeldEvent) {
        val game = findGameForPlayer(event.player) ?: return
        val data = game.playerDatas.filterIsInstance<PlayerData>()
            .firstOrNull { it.uniqueId == event.player.uniqueId } ?: return
        if (game.canPerform(data.uniqueId, CooperativeAction.CHANGE_HOTBAR)) return
        event.isCancelled = true
        event.player.sendActionBar(miniMessage.deserialize("<red>공동 역할상 핫바를 바꿀 수 없습니다."))
    }
}
