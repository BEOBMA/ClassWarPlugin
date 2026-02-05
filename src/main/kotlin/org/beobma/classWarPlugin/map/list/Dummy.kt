package org.beobma.classWarPlugin.map.list

import org.beobma.classWarPlugin.manager.GameManager.gameWorld
import org.beobma.classWarPlugin.map.Map
import org.bukkit.Location

class Dummy : Map() {
    override val name: String
        get() = "<white><bold>맵 이름</bold><gray>"
    override val size: Pair<Location, Location>
        get() = Pair(Location(gameWorld, 0.0, 0.0, 0.0), Location(gameWorld, 0.0, 0.0, 0.0))
    override val redTeamStartLocation: Location
        get() = Location(gameWorld, 0.0, 0.0, 0.0)
    override val blueTeamStartLocation: Location
        get() = Location(gameWorld, 0.0, 0.0, 0.0)
    override val spectatorTeamStartLocation: Location
        get() = Location(gameWorld, 0.0, 0.0, 0.0)
}