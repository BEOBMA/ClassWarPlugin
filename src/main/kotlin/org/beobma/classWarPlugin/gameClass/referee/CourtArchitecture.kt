package org.beobma.classWarPlugin.gameClass.referee

import org.beobma.classWarPlugin.domain.DomainBlock
import org.bukkit.Material
import kotlin.math.*

/** Symmetric monumental court. Coordinates are unique and the central trial positions stay open. */
internal object CourtArchitecture {
    fun plan(radius: Int): List<DomainBlock> {
        val blocks = linkedMapOf<Triple<Int, Int, Int>, DomainBlock>()
        fun put(x: Int, y: Int, z: Int, material: Material) {
            if (y !in -1 until radius || x * x + y * y + z * z >= (radius - 1) * (radius - 1)) return
            // Defendant (0,0,0), judge (0,0,-3), and a three-block-wide approach aisle.
            if (y in 0..2 && abs(x) <= 1 && z in -3..radius) return
            blocks[Triple(x, y, z)] = DomainBlock(x, y, z, material)
        }
        val inner = radius - 1
        for (x in -inner..inner) for (z in -inner..inner) {
            val distance = sqrt((x * x + z * z).toDouble())
            val material = when {
                distance >= inner - 1.3 -> Material.CHISELED_POLISHED_BLACKSTONE
                abs(x) <= 1 -> Material.RED_NETHER_BRICKS
                abs(x) == 2 -> Material.POLISHED_BASALT
                abs(z) % 4 == 0 && abs(x) % 3 == 0 -> Material.GILDED_BLACKSTONE
                (abs(x) + abs(z)) % 2 == 0 -> Material.DEEPSLATE_TILES
                else -> Material.POLISHED_BLACKSTONE_BRICKS
            }
            put(x, -1, z, material)
        }
        val span = (radius * 0.5).toInt().coerceIn(2, 9)
        val rear = -(radius * 0.55).toInt().coerceAtLeast(2)
        // Raised bench, recessed throne, and a stepped monumental rear wall.
        for (x in -span + 1 until span) {
            put(x, 0, rear, Material.POLISHED_BLACKSTONE_BRICKS)
            put(x, 1, rear, if (abs(x) % 2 == 0) Material.CHISELED_POLISHED_BLACKSTONE else Material.POLISHED_DEEPSLATE)
            put(x, 2, rear, Material.POLISHED_BLACKSTONE_BRICK_SLAB)
            put(x, 0, rear - 1, Material.DEEPSLATE_TILES)
            val height = (5 - abs(x)).coerceAtLeast(2)
            for (y in 1..height) put(x, y, rear - 2,
                if (abs(x) == 1) Material.RED_NETHER_BRICKS else Material.DEEPSLATE_BRICKS)
        }
        put(0, 1, rear - 1, Material.CHISELED_POLISHED_BLACKSTONE)
        // Paired pillars with broad bases, capitals, and stepped vaulted ribs.
        for (z in listOf(rear + 1, 1, span - 1).distinct()) {
            for (side in listOf(-1, 1)) {
                val x = side * span
                for (dx in -1..1) for (dz in -1..1) put(x + dx, 0, z + dz, Material.POLISHED_BLACKSTONE_BRICKS)
                for (y in 1..5) put(x, y, z,
                    if (y == 1 || y == 5) Material.CHISELED_POLISHED_BLACKSTONE else Material.POLISHED_BASALT)
                for (dx in -1..1) put(x + dx, 5, z, Material.DEEPSLATE_TILE_SLAB)
            }
            for (x in -span..span) {
                val y = 5 + (2 * (1.0 - abs(x).toDouble() / span)).roundToInt()
                put(x, y, z, Material.DEEPSLATE_BRICKS)
            }
        }
        // Separate bench seats and high backs; the center aisle never contains furniture.
        for (z in 2 until inner - 1 step 3) for (side in listOf(-1, 1)) for (width in 3..span) {
            val x = side * width
            put(x, 0, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB)
            put(x, 0, z + 1, Material.POLISHED_DEEPSLATE)
            put(x, 1, z + 1, Material.DEEPSLATE_TILE_SLAB)
        }
        // Witness stands flank the accused, with dark railings marking the chamber.
        for (side in listOf(-1, 1)) {
            put(side * 3, 0, -1, Material.CHISELED_POLISHED_BLACKSTONE)
            put(side * 3, 1, -1, Material.POLISHED_BLACKSTONE_BRICK_SLAB)
            for (z in -1..1) put(side * 2, 0, z, Material.POLISHED_BLACKSTONE_BRICK_WALL)
        }
        // Solid relief of the scales, suspended against the rear wall rather than extra particles.
        for (x in -2..2) put(x, 5, rear - 1, Material.GILDED_BLACKSTONE)
        for (y in 3..4) {
            put(0, y, rear - 1, Material.CHISELED_POLISHED_BLACKSTONE)
            for (side in listOf(-2, 2)) put(side, y, rear - 1, Material.IRON_CHAIN)
        }
        for (side in listOf(-2, 2)) for (dx in -1..1)
            put(side + dx, 2, rear - 1, Material.POLISHED_BLACKSTONE_BRICK_SLAB)
        return blocks.values.toList()
    }
}
