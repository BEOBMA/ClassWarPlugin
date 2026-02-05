package org.beobma.classWarPlugin.map.list

import org.beobma.classWarPlugin.manager.GameManager.gameWorld
import org.beobma.classWarPlugin.map.Map
import org.bukkit.Location

class TrainingGround : Map() {
    override val name: String
        get() = "<white><bold>훈련장</bold><gray>"
    override val size: Pair<Location, Location>
        get() = Pair(Location(gameWorld, 31.5, -44.0, -26.5), Location(gameWorld, 63.5, -61.0, -58.5))
    override val redTeamStartLocation: Location
        get() = Location(gameWorld, 33.5, -60.0, -27.5, -135f, 0f)
    override val blueTeamStartLocation: Location
        get() = Location(gameWorld, 33.5, -60.0, -27.5, -135f, 0f)
    override val spectatorTeamStartLocation: Location
        get() = Location(gameWorld, 33.5, -60.0, -27.5, -135f, 0f)
}