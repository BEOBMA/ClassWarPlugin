package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.GameManager.handleDeath
import org.beobma.classWarPlugin.manager.GameManager.recordPlayerKill
import org.beobma.classWarPlugin.manager.DamageManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.beobma.classWarPlugin.gameClass.list.GraveRobber
import org.beobma.classWarPlugin.gameClass.list.Hacker
import org.beobma.classWarPlugin.gameClass.list.AreaDevelopment
import org.beobma.classWarPlugin.gameClass.list.Mathematician
import org.beobma.classWarPlugin.gameClass.list.Vampire
import org.beobma.classWarPlugin.gameClass.list.PortalGun
import org.beobma.classWarPlugin.gameClass.list.Contractor
import org.beobma.classWarPlugin.gameClass.list.DeathNote
import org.beobma.classWarPlugin.gameClass.list.Levatain
import org.beobma.classWarPlugin.gameClass.list.Referee
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.gameClass.list.HideAndSeek

class OnPlayerDeathEvent : Listener{
    private val miniMessage = MiniMessage.miniMessage()

    @EventHandler
    fun onPlayerDeathEvent(event: PlayerDeathEvent) {
        val player = event.player
        HideAndSeek.handlePlayerDeath(player.uniqueId)
        val currentGame = game ?: return
        val playerData = currentGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.player.uniqueId == player.uniqueId } ?: return
        playerData.gameClasses.forEach { assignedClass ->
            assignedClass.passives.filterIsInstance<PlayerDeathHandler>().forEach { it.onPlayerDeath() }
            (assignedClass as? PlayerDeathHandler)?.onPlayerDeath()
        }
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
        Hacker.clearSessions(listOf(player.uniqueId))
        Mathematician.clearSessions(listOf(player.uniqueId))
        Vampire.clearForms(listOf(player.uniqueId))
        val killerId = attribution?.attackerId ?: player.killer?.uniqueId
        Referee.recordMurder(currentGame, killerId, playerData)
        currentGame.recordPlayerKill(player.uniqueId, killerId)
        AreaDevelopment.handlePlayerDeath(playerData, killerId)
        Levatain.handleKill(killerId)
        Contractor.clearSessions(listOf(player.uniqueId))
        DeathNote.clearSessions(listOf(player.uniqueId))
        PortalGun.clearForPlayers(listOf(player.uniqueId))
        AreaDevelopment.clearDomains(listOf(player.uniqueId))
        GraveRobber.recordDeath(playerData)
        handleDeath(playerData)
    }
}
