package org.beobma.classWarPlugin.info

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object Info {
    var game: Game? = null
    val world = Bukkit.getWorlds().first()
    private val trainingGames: MutableMap<UUID, Game> = mutableMapOf()

    fun isGaming(): Boolean {
        return game != null
    }

    fun isTraining(player: Player? = null): Boolean {
        return if (player == null) {
            trainingGames.isNotEmpty()
        } else {
            trainingGames.containsKey(player.uniqueId)
        }
    }

    fun findGame(player: Player): Game? {
        val activeGame = game
        if (activeGame?.playerDatas?.any { it.player == player } == true) {
            return activeGame
        }
        return trainingGames[player.uniqueId]
    }

    fun findPlayerData(player: Player): PlayerData? {
        val activeGame = game
        val livePlayerData = activeGame?.playerDatas?.find { it.player == player }
        if (livePlayerData != null) {
            return livePlayerData
        }
        return trainingGames[player.uniqueId]?.playerDatas?.find { it.player == player }
    }

    fun registerTrainingGame(player: Player, trainingGame: Game) {
        trainingGames[player.uniqueId] = trainingGame
    }

    fun clearTrainingGame(player: Player) {
        trainingGames.remove(player.uniqueId)
    }
}
