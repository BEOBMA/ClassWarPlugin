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

    fun inject(playerData: PlayerData) {
        if (playerData.entityStatus !is PlayerStatus) return

        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.entityStatus
        this.game = playerData.initGame
    }
}
