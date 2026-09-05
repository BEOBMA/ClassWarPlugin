package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.GameManager.handleCombatDeath
import org.beobma.classWarPlugin.manager.GameManager.recordPlayerKill
import org.beobma.classWarPlugin.manager.GameManager.rewardKiller
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
        val attribution = DamageManager.consumeAttribution(player)
        val killerName = attribution?.takeIf { it.attackerId != player.uniqueId }?.attackerName
            ?: player.killer?.name
        event.deathMessage(null)
        if (currentGame.settings.deathMessagesEnabled) {
            val cause = attribution?.path?.displayName ?: describeDamageCause(player.lastDamageCause?.cause?.name)
            val message = buildDeathMessage(
                victimName = player.name,
                killerName = killerName.takeIf { currentGame.settings.deathMessagesShowKiller },
                cause = cause.takeIf { currentGame.settings.deathMessagesShowCause },
            )
            val component = miniMessage.deserialize(message)
            currentGame.playerDatas.filterIsInstance<PlayerData>()
                .map { it.player }
                .filter { it.isOnline }
                .distinctBy { it.uniqueId }
                .forEach { it.sendMessage(component) }
        }
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
        val outcome = handleCombatDeath(playerData)
        rewardKiller(killerId, player.uniqueId, outcome)
    }

    private fun buildDeathMessage(victimName: String, killerName: String?, cause: String?): String {
        val prefix = "<red><bold>[탈락]</bold> <white>$victimName<gray>님이"
        val result = if (killerName != null) {
            "$prefix <white>$killerName<gray>님에게 처치되었습니다."
        } else {
            "$prefix 사망했습니다."
        }
        return if (cause != null) "$result <dark_gray>(사유: $cause<dark_gray>)" else result
    }

    private fun describeDamageCause(causeName: String?): String = when (causeName) {
        "CONTACT" -> "<white>접촉 피해"
        "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK" -> "<white>근접 공격"
        "PROJECTILE" -> "<white>투사체"
        "SUFFOCATION" -> "<white>질식"
        "FALL" -> "<white>추락"
        "FIRE", "FIRE_TICK", "CAMPFIRE", "HOT_FLOOR", "LAVA" -> "<white>화염"
        "DROWNING" -> "<white>익사"
        "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> "<white>폭발"
        "VOID" -> "<white>공허"
        "LIGHTNING" -> "<white>번개"
        "STARVATION" -> "<white>굶주림"
        "POISON" -> "<white>독"
        "MAGIC", "DRAGON_BREATH", "WITHER" -> "<white>마법 또는 상태이상"
        "FALLING_BLOCK" -> "<white>낙하 블록"
        "THORNS" -> "<white>가시"
        "FLY_INTO_WALL" -> "<white>비행 충돌"
        "CRAMMING" -> "<white>압사"
        "DRYOUT" -> "<white>건조"
        "FREEZE" -> "<white>동사"
        "SONIC_BOOM" -> "<white>음파 공격"
        "WORLD_BORDER" -> "<white>자기장"
        "KILL", "SUICIDE" -> "<white>즉사"
        "CUSTOM" -> "<white>특수 효과"
        else -> "<white>알 수 없는 원인"
    }
}
