package org.beobma.classWarPlugin.entity

import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.scheduler.BukkitTask

abstract class EntityData {
    abstract val game: Game
    abstract val entityStatus: EntityStatus
    abstract val bukkitTasks: MutableList<BukkitTask>
    abstract val statusAbnormalitys: MutableList<StatusAbnormality>
}