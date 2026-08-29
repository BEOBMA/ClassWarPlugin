package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.description.DescriptionText
import org.bukkit.entity.Player

abstract class Passive : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    open val briefDescription: List<String>
        get() = DescriptionText.brief(description)

    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }
}
