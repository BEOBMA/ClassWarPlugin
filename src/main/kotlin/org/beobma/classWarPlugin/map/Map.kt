package org.beobma.classWarPlugin.map

import org.bukkit.Location

abstract class Map() {
    abstract val name: String
    abstract val size: Pair<Location, Location>
    abstract val redTeamStartLocation: Location
    abstract val blueTeamStartLocation: Location
    abstract val spectatorTeamStartLocation: Location
}
