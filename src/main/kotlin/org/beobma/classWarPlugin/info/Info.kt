package org.beobma.classWarPlugin.info

import org.beobma.classWarPlugin.game.Game
import org.bukkit.Bukkit

object Info {
    var game: Game? = null
    val world = Bukkit.getWorlds().first()

    fun isGaming(): Boolean {
        return game != null
    }
}