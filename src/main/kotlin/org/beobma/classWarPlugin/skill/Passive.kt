package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.player.PlayerStatus
import org.bukkit.entity.Player

abstract class Passive {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>

    fun inject(playerData: PlayerData) {
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.playerStatus
        this.game = playerData.game
    }
}
