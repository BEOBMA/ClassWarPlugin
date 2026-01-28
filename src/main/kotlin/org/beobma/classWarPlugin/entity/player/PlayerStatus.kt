package org.beobma.classWarPlugin.entity.player

import org.beobma.classWarPlugin.entity.EntityStatus
import org.bukkit.entity.Player

data class PlayerStatus(
    val player: Player
) : EntityStatus()
