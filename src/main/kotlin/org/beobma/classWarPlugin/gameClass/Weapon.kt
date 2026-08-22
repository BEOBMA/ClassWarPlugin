package org.beobma.classWarPlugin.gameClass

import org.bukkit.Material

abstract class Weapon {
    abstract val name: String
    abstract val description: List<String>
    abstract val material: Material
}

object DefaultWeapon : Weapon() {
    override val name = "<gray>철 검"
    override val description: List<String> = emptyList()
    override val material = Material.IRON_SWORD
}
