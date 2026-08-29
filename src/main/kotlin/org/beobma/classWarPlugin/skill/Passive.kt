package org.beobma.classWarPlugin.skill

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.description.DescriptionText
import org.bukkit.entity.Player

/**
 * 클래스에 상시 부착되는 지속 효과의 기반 형식이다.
 * 구현체의 콜백을 사용하기 전에 [inject]로 소유자를 연결해야 한다.
 */
abstract class Passive : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    open val briefDescription: List<String>
        get() = DescriptionText.brief(description)

    /** 패시브가 참조할 플레이어·상태·경기를 [playerData] 기준으로 연결한다. */
    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }
}
