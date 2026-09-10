package org.beobma.classWarPlugin.info

import org.beobma.classWarPlugin.game.Game

object Info {
    var game: Game? = null

    fun isGaming(): Boolean {
        return game != null
    }
}