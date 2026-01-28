package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.map.Map
import org.bukkit.scheduler.BukkitTask

data class Game(
    val playerDatas: MutableList<EntityData>,
    var map: Map? = null,
    var classPickOrder: MutableList<PlayerData> = mutableListOf(),
    val classList: MutableList<GameClass?> = mutableListOf(),
    val mapList: MutableList<Map> = mutableListOf(),
    val tasks: MutableList<BukkitTask> = mutableListOf()
)
