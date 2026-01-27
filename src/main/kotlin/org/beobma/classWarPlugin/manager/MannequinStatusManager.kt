package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.bukkit.entity.Entity
import java.util.UUID

object MannequinStatusManager {
    private val statuses: MutableMap<UUID, EntityStatus> = mutableMapOf()

    fun getStatus(entity: Entity): EntityStatus? {
        if (!entity.isMannequin()) {
            return null
        }
        return statuses.getOrPut(entity.uniqueId) { EntityStatus(entity) }
    }

    fun clearStatus(entity: Entity) {
        statuses.remove(entity.uniqueId)
    }
}
