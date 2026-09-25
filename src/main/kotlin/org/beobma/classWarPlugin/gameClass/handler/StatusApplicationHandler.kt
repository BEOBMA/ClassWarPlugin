package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.status.StatusAbnormality

/** Called after a positive grant has actually left a status on its target. */
interface StatusApplicationHandler {
    fun onStatusApplied(target: EntityData, status: StatusAbnormality)
}
