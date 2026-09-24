package org.beobma.classWarPlugin.gameClass.creator

import org.beobma.classWarPlugin.domain.DomainBlock
import org.bukkit.Material
import kotlin.math.*

/** A gilded creation hall: walkable inlays, eight buttressed towers, a suspended celestial crown. */
internal object CreationArchitecture {
    fun build(radius: Int): List<DomainBlock> {
        val blocks = linkedMapOf<Triple<Int, Int, Int>, DomainBlock>()
        fun put(x: Int, y: Int, z: Int, material: Material) {
            if (y !in -1 until radius || x*x + y*y + z*z >= (radius-1)*(radius-1)) return
            blocks[Triple(x,y,z)] = DomainBlock(x,y,z,material)
        }
        val outer = radius - 5
        for (x in -outer..outer) for (z in -outer..outer) {
            val d = sqrt((x*x + z*z).toDouble())
            val ring = listOf(5.0, 11.0, outer.toDouble()).any { abs(d-it) < 0.6 }
            if (ring) put(x,-1,z,Material.GOLD_BLOCK)
            else if (d < outer && (abs(x) <= 1 || abs(z) <= 1 || abs(abs(x)-abs(z)) == 0))
                put(x,-1,z,if ((abs(x)+abs(z)) % 5 == 0) Material.SEA_LANTERN else Material.QUARTZ_BLOCK)
            else if (d < outer && (x+z) % 6 == 0) put(x,-1,z,Material.CHISELED_POLISHED_BLACKSTONE)
        }
        repeat(8) { n ->
            val angle = n * PI / 4
            val x = (cos(angle) * (outer-2)).roundToInt()
            val z = (sin(angle) * (outer-2)).roundToInt()
            for (dx in -2..2) for (dz in -2..2) {
                put(x+dx,0,z+dz,Material.POLISHED_DEEPSLATE)
                if (abs(dx)+abs(dz) <= 2) put(x+dx,1,z+dz,Material.CHISELED_QUARTZ_BLOCK)
            }
            for (y in 2..11) for (dx in -1..1) for (dz in -1..1) {
                val corner = abs(dx)==1 && abs(dz)==1
                put(x+dx,y,z+dz,when {
                    y==2 || y==10 -> Material.GOLD_BLOCK
                    dx==0 && dz==0 -> Material.SEA_LANTERN
                    corner -> Material.QUARTZ_PILLAR
                    else -> Material.POLISHED_BLACKSTONE
                })
            }
            for (dx in -2..2) for (dz in -2..2) put(x+dx,12,z+dz,Material.CHISELED_QUARTZ_BLOCK)
            put(x,13,z,Material.SEA_LANTERN)
            // Ribs rise inward; no bars across the player's head-height combat space.
            for (step in 0..9) {
                val r = outer-2-step
                put((cos(angle)*r).roundToInt(),12+step/2,(sin(angle)*r).roundToInt(),Material.GOLD_BLOCK)
            }
        }
        for (degrees in 0 until 360) {
            val a = degrees * PI / 180
            val x = (cos(a)*8).roundToInt(); val z = (sin(a)*8).roundToInt()
            put(x,16,z,Material.GOLD_BLOCK)
            put(x,17,z,Material.CHISELED_QUARTZ_BLOCK)
            if (degrees % 45 == 0) {
                for (y in 11..15) put(x,y,z,Material.IRON_CHAIN)
                put(x,10,z,Material.SEA_LANTERN)
            }
        }
        // Arcaded outer cloister. The central twelve-block-radius arena remains unobstructed.
        repeat(8) { sector ->
            for (step in 0..28) {
                val t = step / 28.0
                val a = (sector + t) * PI / 4
                val x = (cos(a)*(outer-2)).roundToInt()
                val z = (sin(a)*(outer-2)).roundToInt()
                val y = (9 + sin(t*PI)*4).roundToInt()
                put(x,y,z,Material.QUARTZ_BRICKS)
                put(x,y+1,z,Material.GOLD_BLOCK)
                if (step % 7 == 0) put(x,y-1,z,Material.SEA_LANTERN)
            }
            val a = (sector+0.5)*PI/4
            val cx = (cos(a)*(outer+1)).roundToInt()
            val cz = (sin(a)*(outer+1)).roundToInt()
            for (y in 0..8) for (side in -2..2) {
                val x = cx + (-sin(a)*side).roundToInt()
                val z = cz + (cos(a)*side).roundToInt()
                put(x,y,z,when {
                    y == 0 || y == 8 || abs(side)==2 -> Material.CHISELED_QUARTZ_BLOCK
                    y == 4 || side == 0 -> Material.GOLD_BLOCK
                    else -> Material.LIGHT_BLUE_STAINED_GLASS
                })
            }
        }
        // A faceted suspended source crystal, surrounded by two interlocking halo rings.
        for (y in 16..22) for (x in -3..3) for (z in -3..3) {
            val width = 3-abs(y-19)
            if (abs(x)+abs(z) <= width) put(x,y,z,
                if (x==0 && z==0) Material.SEA_LANTERN else if ((x+z+y)%2==0) Material.AMETHYST_BLOCK else Material.LIGHT_BLUE_STAINED_GLASS)
        }
        for (degrees in 0 until 360 step 2) {
            val a = degrees*PI/180
            put((cos(a)*5).roundToInt(),(19+sin(a)*2).roundToInt(),(sin(a)*5).roundToInt(),Material.GOLD_BLOCK)
            put((cos(a)*5).roundToInt(),(19-sin(a)*2).roundToInt(),(sin(a)*5).roundToInt(),Material.QUARTZ_BLOCK)
        }
        return blocks.values.toList()
    }
}
