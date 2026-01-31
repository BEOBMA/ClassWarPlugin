package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.keyword.Dictionary
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.EntityType

object UtilManager {
    val dictionary = Dictionary
    val miniMessage = MiniMessage.miniMessage()
    private val keywordTokenRegex = "\\{keyword:([A-Za-z]+)}".toRegex()
    private val keywordTokens = enumValues<Keyword>().associateBy { it.name }
    private val dyeMaterials = listOf(
        Material.WHITE_DYE,
        Material.ORANGE_DYE,
        Material.MAGENTA_DYE,
        Material.LIGHT_BLUE_DYE,
        Material.YELLOW_DYE,
        Material.LIME_DYE,
        Material.PINK_DYE,
        Material.GRAY_DYE,
        Material.LIGHT_GRAY_DYE,
        Material.CYAN_DYE,
        Material.PURPLE_DYE,
        Material.BLUE_DYE,
        Material.BROWN_DYE,
        Material.GREEN_DYE,
        Material.RED_DYE,
        Material.BLACK_DYE
    )

    fun applyKeywords(text: String): String {
        return keywordTokenRegex.replace(text) { match ->
            keywordTokens[match.groupValues[1]]?.string ?: match.value
        }
    }

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

    fun Player.sendMiniMessage(message: String) {
        this.sendMessage(miniMessage.deserialize(message))
    }

    fun Entity.isMannequin(): Boolean {
        return this.type == EntityType.MANNEQUIN
    }

    fun Player.resetDyeCooldowns() {
        dyeMaterials.forEach { dye ->
            setCooldown(dye, 0)
        }
    }
}

inline fun <reified T> Iterable<*>.forEachIs(action: (T) -> Unit) {
    for (item in this) {
        if (item is T) {
            action(item)
        }
    }
}
