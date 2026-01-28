package org.beobma.classWarPlugin.entity.player

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask

class PlayerData(
    val player: Player,
    val initGame: Game,
    var team: TeamType? = null,
    var gameClass: GameClass? = null,
) : EntityData() {
    override val entity: Entity = player
    override val game: Game = initGame
    override val entityStatus: EntityStatus = PlayerStatus(player)
    override val bukkitTasks: MutableList<BukkitTask> = mutableListOf()
    override val statusAbnormalitys: MutableList<StatusAbnormality> = mutableListOf()

    fun trackTask(task: BukkitTask): BukkitTask {
        bukkitTasks.add(task)
        game.tasks.add(task)
        return task
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerData) return false

        return player.uniqueId == other.player.uniqueId
    }

    override fun hashCode(): Int {
        return player.uniqueId.hashCode()
    }
}
