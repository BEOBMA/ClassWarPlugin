package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityStatus
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.bukkit.entity.Entity
import java.util.UUID

object MannequinStatusManager {
    private val datas: MutableMap<UUID, DummyEntityData> = mutableMapOf()
    private val statuses: MutableMap<UUID, DummyEntityStatus> = mutableMapOf()

    fun getData(entity: Entity, game: Game): EntityData? {
        if (!entity.isMannequin()) {
            return null
        }
        return datas.getOrPut(entity.uniqueId) { DummyEntityData(entity, game) }
    }

    fun getStatus(entity: Entity): EntityStatus? {
        if (!entity.isMannequin()) {
            return null
        }
        return statuses.getOrPut(entity.uniqueId) { DummyEntityStatus(entity) }
    }

    fun clearData(entity: Entity) {
        datas.remove(entity.uniqueId)
    }
    fun clearStatus(entity: Entity) {
        statuses.remove(entity.uniqueId)
    }
}
