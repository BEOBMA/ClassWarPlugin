package org.beobma.classWarPlugin.entity.player

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class PlayerData(
    var player: Player,
    val initGame: Game,
    var gameClass: GameClass? = null,
) : EntityData() {
    val uniqueId: UUID = player.uniqueId
    override val entity: Entity
        get() = player
    override val game: Game = initGame
    override val entityStatus: EntityStatus = PlayerStatus()
    override val bukkitTasks: MutableList<BukkitTask> = mutableListOf()
    override val statusAbnormalitys: MutableList<StatusAbnormality> = mutableListOf()

    fun trackTask(task: BukkitTask): BukkitTask {
        bukkitTasks.add(task)
        game.tasks.add(task)
        return task
    }

    fun isEnemyOf(other: PlayerData): Boolean = this != other

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerData) return false

        return uniqueId == other.uniqueId
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
    }
}
