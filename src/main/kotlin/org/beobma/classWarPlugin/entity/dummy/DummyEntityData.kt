package org.beobma.classWarPlugin.entity.dummy

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask

class DummyEntityData(
    val entity: Entity,
    val initGame: Game,
) : EntityData() {
    override val game: Game = initGame
    override val entityStatus: EntityStatus = DummyEntityStatus(entity)
    override val bukkitTasks: MutableList<BukkitTask> = mutableListOf()
    override val statusAbnormalitys: MutableList<StatusAbnormality> = mutableListOf()

}