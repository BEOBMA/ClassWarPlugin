package org.beobma.classWarPlugin.domain

import org.bukkit.Material

/** Optional reusable scenery; classes can supply their own relative block plans instead. */
object DomainInteriors {
    fun courtroom(radius: Int): List<DomainBlock> = buildList {
        val width = (radius / 2).coerceAtLeast(2)
        val depth = (radius / 2).coerceAtLeast(2)
        for (x in -width..width) {
            add(DomainBlock(x, 0, -depth, Material.DARK_OAK_PLANKS))
            add(DomainBlock(x, 1, -depth, Material.DARK_OAK_PLANKS))
        }
        for (z in 1 until depth step 2) for (x in -width..width) {
            if (x !in -1..1) add(DomainBlock(x, 0, z, Material.DARK_OAK_PLANKS))
        }
        for (x in listOf(-width, width)) for (y in 0..3) add(DomainBlock(x, y, -depth, Material.QUARTZ_PILLAR))
    }.filter { it.x * it.x + it.y * it.y + it.z * it.z < (radius - 1) * (radius - 1) }

    fun ruinedCity(radius: Int): List<DomainBlock> = buildList {
        for (x in -radius + 2 until radius - 1 step 4) for (z in -radius + 2 until radius - 1 step 4) {
            if (kotlin.math.abs(x) < 3 || kotlin.math.abs(z) < 3) continue
            val height = 2 + kotlin.math.abs(x * 31 + z * 17) % 5
            for (y in 0 until height) for (dx in 0..1) {
                val bx = x + dx
                if (bx * bx + y * y + z * z < (radius - 2) * (radius - 2))
                    add(DomainBlock(bx, y, z, if ((bx + y + z) % 3 == 0) Material.CRACKED_STONE_BRICKS else Material.STONE_BRICKS))
            }
        }
    }
}
