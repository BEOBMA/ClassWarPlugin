package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.GameManager.handleDeath
import org.beobma.classWarPlugin.manager.DamageManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class OnPlayerDeathEvent : Listener{
    private val miniMessage = MiniMessage.miniMessage()

    @EventHandler
    fun onPlayerDeathEvent(event: PlayerDeathEvent) {
        val player = event.player
        val currentGame = game ?: return
        val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.player.uniqueId == player.uniqueId } ?: return
        val attribution = DamageManager.consumeAttribution(player)
        val killerName = attribution?.takeIf { it.attackerId != player.uniqueId }?.attackerName
            ?: player.killer?.name
        val message = when {
            killerName == null ->
                "<red><bold>[탈락]</bold> <white>${player.name}<gray>님이 사망했습니다."
            attribution != null ->
                "<red><bold>[탈락]</bold> <white>${player.name}<gray>님이 <white>$killerName<gray>님의 ${attribution.path.displayName}<gray>으로 처치되었습니다."
            else ->
                "<red><bold>[탈락]</bold> <white>${player.name}<gray>님이 <white>$killerName<gray>님에게 처치되었습니다."
        }
        event.deathMessage(miniMessage.deserialize(message))
        event.drops.clear()
        event.droppedExp = 0
        player.gameMode = GameMode.SPECTATOR
        handleDeath(playerData)
    }
}
