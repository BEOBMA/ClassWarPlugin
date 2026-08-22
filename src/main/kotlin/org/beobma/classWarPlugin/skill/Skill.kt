package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.bukkit.entity.Player

abstract class Skill : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    open val id: String
        get() = javaClass.name
    abstract val description: List<String>
    abstract val cooldown: Int?

    open val isOnOffSKill: Boolean = false
    open val canUseWhileSilenced: Boolean = false

    private var activeContext: SkillContext? = null

    abstract fun use()
    open fun isUseSuccess(): Boolean = true

    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }

    internal fun execute(context: SkillContext) {
        activeContext = context
        try {
            use()
        } finally {
            activeContext = null
        }
    }

    protected fun multiplyCurrentCooldown(multiplier: Double) {
        activeContext?.multiplyCooldown(multiplier)
    }
}
