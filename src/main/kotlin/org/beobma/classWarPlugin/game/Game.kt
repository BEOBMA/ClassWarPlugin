package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.map.Map
import org.beobma.classWarPlugin.player.PlayerData

data class Game(
    val playerDatas: MutableList<PlayerData>,
    var map: Map? = null,
    var classPickOrder: MutableList<PlayerData> = mutableListOf(),
    val classList: MutableList<GameClass?> = mutableListOf(),
    val mapList: MutableList<Map> = mutableListOf()
)