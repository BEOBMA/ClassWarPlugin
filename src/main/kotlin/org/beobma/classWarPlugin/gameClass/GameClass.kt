package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.player.PlayerStatus
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

abstract class GameClass() {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    abstract val classItemMaterial: Material
    abstract val weapon: Weapon
    abstract val skills: List<Skill>
    abstract var passives: List<Passive>
    open val extraItemMaterials: List<ItemStack> = listOf()

    fun inject(playerData: PlayerData) {
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.playerStatus
        this.game = playerData.game
    }
}
