package org.beobma.classWarPlugin.gameClass

import org.bukkit.Material

abstract class Weapon {
    abstract val name: String
    abstract val description: List<String>
    abstract val material: Material
}