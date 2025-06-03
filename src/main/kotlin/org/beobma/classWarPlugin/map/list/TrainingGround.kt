package org.beobma.classWarPlugin.map.list

import org.beobma.classWarPlugin.info.Info.world
import org.beobma.classWarPlugin.map.Map
import org.bukkit.Location

class TrainingGround : Map() {
    override val name: String
        get() = "<white><bold>훈련장</bold><gray>"
    override val size: Pair<Location, Location>
        get() = Pair(Location(world, 0.0, 0.0, 0.0), Location(world, 0.0, 0.0, 0.0))
    override val redTeamStartLocation: Location
        get() = Location(world, 0.0, 0.0, 0.0)
    override val blueTeamStartLocation: Location
        get() = Location(world, 0.0, 0.0, 0.0)
    override val spectatorTeamStartLocation: Location
        get() = Location(world, 0.0, 0.0, 0.0)
}