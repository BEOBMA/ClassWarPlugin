package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.entity.player.PlayerStatus
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 플레이어에게 배정되는 클래스의 공통 계약이다.
 *
 * 인스턴스를 사용하기 전에 [inject]로 소유자를 연결해야 한다. 클래스가 가진 [skills]와
 * [passives]도 배정 과정에서 같은 [PlayerData]로 주입된다.
 */
abstract class GameClass : EffectApiAccess {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val rank: Rank
    abstract val classItemMaterial: Material
    open val weapon: Weapon = DefaultWeapon
    abstract val skills: List<Skill>
    abstract var passives: List<Passive>
    open val extraItemMaterials: List<ItemStack> = listOf()

    /** 클래스가 참조할 플레이어·상태·경기를 [playerData] 기준으로 연결한다. */
    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return

        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }

    /** 현재 주입된 모든 컨텍스트가 [expected]와 일치하는지 검사한다. */
    fun isInjectedFor(expected: PlayerData): Boolean =
        this::playerData.isInitialized &&
            this::player.isInitialized &&
            this::playerStatus.isInitialized &&
            this::game.isInitialized &&
            playerData === expected &&
            player.uniqueId == expected.uniqueId &&
            game === expected.initGame
}
