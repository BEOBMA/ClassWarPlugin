package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.keyword.Dictionary
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player

object UtilManager {
    val dictionary = Dictionary().dictionary
    val miniMessage = MiniMessage.miniMessage()

    fun Player.getPlayerMaxHealth(): Double {
        return this.getAttribute(Attribute.MAX_HEALTH)!!.baseValue
    }

    fun Player.setPlayerMaxHealth(value: Double) {
        this.getAttribute(Attribute.MAX_HEALTH)!!.baseValue = value
    }

    fun Player.isInArea(loc1: Location, loc2: Location): Boolean {
        val xMin = minOf(loc1.x, loc2.x)
        val xMax = maxOf(loc1.x, loc2.x)
        val yMin = minOf(loc1.y, loc2.y)
        val yMax = maxOf(loc1.y, loc2.y)
        val zMin = minOf(loc1.z, loc2.z)
        val zMax = maxOf(loc1.z, loc2.z)

        val playerLocation = location

        return (playerLocation.x in xMin..xMax &&
                playerLocation.y in yMin..yMax &&
                playerLocation.z in zMin..zMax)
    }
}