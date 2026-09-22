package org.beobma.classWarPlugin.domain

import org.bukkit.Material

internal object DomainLighting {
    fun canFill(radius: Int, x: Int, y: Int, z: Int, material: Material): Boolean =
        DomainShell.isInterior(radius, x, y, z) &&
            when (material) {
                Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.LIGHT -> true
                else -> false
            }
}
