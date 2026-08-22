package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.bukkit.entity.Player

abstract class Passive : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    open val summary: List<String>
        get() = description.filter { it.isNotBlank() && !Keyword.isExplanation(it) }.take(2)

    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }
}
