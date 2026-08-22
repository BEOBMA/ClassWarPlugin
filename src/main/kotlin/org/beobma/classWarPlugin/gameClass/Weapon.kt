package org.beobma.classWarPlugin.gameClass

import org.bukkit.Material
import org.beobma.classWarPlugin.keyword.Keyword

abstract class Weapon {
    abstract val name: String
    abstract val description: List<String>
    open val summary: List<String>
        get() = description.filter { it.isNotBlank() && !Keyword.isExplanation(it) }.take(2)
    abstract val material: Material
}

object DefaultWeapon : Weapon() {
    override val name = "<gray>철 검"
    override val description: List<String> = emptyList()
    override val material = Material.IRON_SWORD
}
