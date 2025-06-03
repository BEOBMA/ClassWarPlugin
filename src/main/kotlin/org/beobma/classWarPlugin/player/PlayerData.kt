package org.beobma.classWarPlugin.player

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask

data class PlayerData(
    val player: Player,
    val game: Game,
    var team: TeamType? = null,
    var gameClass: GameClass? = null,
    val bukkitTasks: MutableList<BukkitTask> = mutableListOf(),
    val statusAbnormalitys: MutableList<StatusAbnormality> = mutableListOf(),
    val playerStatus: PlayerStatus = PlayerStatus(player)
)
