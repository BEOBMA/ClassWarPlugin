package org.beobma.classWarPlugin.gameClass.handler

import java.util.UUID

/** Invoked only for a credited kill of another battle participant. */
interface PlayerKillHandler {
    fun onPlayerKill(victimId: UUID)
}
