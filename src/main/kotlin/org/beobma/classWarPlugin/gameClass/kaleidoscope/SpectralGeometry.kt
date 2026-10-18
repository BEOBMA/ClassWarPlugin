package org.beobma.classWarPlugin.gameClass.kaleidoscope

import org.beobma.classWarPlugin.domain.DomainBlock
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.*

internal object SpectralGeometry {
    const val PRISM_LIMIT = 7
    const val LINK_RANGE = 8.0
    const val WAVE_RADIUS = 4.0
    const val WARNING_TICKS = 14
    val glass = listOf(Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
        Material.LIME_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS)

    fun edges(points: List<Vector>): List<Pair<Int, Int>> = points.indices.flatMap { a ->
        (a + 1 until points.size).filter { b -> points[a].distanceSquared(points[b]) <= LINK_RANGE * LINK_RANGE }
            .map { b -> a to b }
    }

    data class Arrival(val tick: Int, val parent: Int?)

    fun arrivals(points: List<Vector>, impact: Vector): Map<Int, Int> = propagation(points,impact).mapValues { it.value.tick }

    /** Keep the shortest-path tree for optical travel without changing wave arrival timings. */
    fun propagation(points: List<Vector>, impact: Vector): Map<Int, Arrival> {
        val links = edges(points)
        val times = IntArray(points.size) { if (points[it].distanceSquared(impact) <= WAVE_RADIUS * WAVE_RADIUS) 0 else Int.MAX_VALUE }
        val settled = mutableSetOf<Int>()
        val parents = arrayOfNulls<Int>(points.size)
        while (true) {
            val a = points.indices.filter { it !in settled && times[it] != Int.MAX_VALUE }.minByOrNull { times[it] } ?: break
            settled += a
            links.forEach { (u, v) ->
                val b = if (u == a) v else if (v == a) u else return@forEach
                val next = times[a] + ceil(points[a].distance(points[b]) / 2).toInt().coerceAtLeast(1)
                if (next < times[b]) { times[b] = next; parents[b] = a }
            }
        }
        return points.indices.filter { times[it] != Int.MAX_VALUE }.associateWith { Arrival(times[it],parents[it]) }
    }

    /** Consistent tangent frame for floor, walls and ceilings, including vertical normals. */
    fun surfaceFrame(normal: Vector): Pair<Vector, Vector> {
        val n = normal.clone().let { if (it.lengthSquared()<1e-8) Vector(0,1,0) else it.normalize() }
        val reference = if (abs(n.y)>0.95) Vector(0,0,1) else Vector(0,1,0)
        val right = reference.crossProduct(n).normalize()
        return right to n.clone().crossProduct(right).normalize()
    }

    fun lattice(radius: Int): List<Vector> = buildList {
        for (x in -18..18 step 6) for (z in -18..18 step 6) for (y in 0..18 step 6) {
            val point = Vector(x.toDouble(), y + 0.8, z.toDouble())
            if (point.length() < radius - 1.0) add(point)
        }
    }

    fun waveTouches(box: BoundingBox, center: Vector, previous: Double, current: Double): Boolean {
        if (!HitboxUtil.intersectsSphere(box, center, current)) return false
        val dx = max(abs(box.minX-center.x), abs(box.maxX-center.x))
        val dy = max(abs(box.minY-center.y), abs(box.maxY-center.y))
        val dz = max(abs(box.minZ-center.z), abs(box.maxZ-center.z))
        return dx*dx + dy*dy + dz*dz >= previous*previous
    }

    /** Twelve repeated mirror wedges and a six-petal rosette, invariant under 60 degree rotations. */
    fun rosette(radius: Double, height: Double, phase: Double, count: Int = 144): List<Vector> = List(count) { i ->
        val angle = i * 2 * PI / count
        val r = radius * (0.62 + 0.38 * cos(6 * (angle-phase)))
        Vector(cos(angle)*r, height, sin(angle)*r)
    }

    /** Dark tessellated floor, framed glass lancets and a suspended rose window.
     * Keep the combat plane empty and cap solid ornament count to bound dissolution displays.
     */
    fun interior(radius: Int): List<DomainBlock> = buildList {
        val tiles=listOf(Material.RED_CONCRETE,Material.ORANGE_CONCRETE,Material.YELLOW_CONCRETE,
            Material.CYAN_CONCRETE,Material.BLUE_CONCRETE,Material.PURPLE_CONCRETE)
        for(x in -radius..radius) for(z in -radius..radius) {
            val r=sqrt((x*x+z*z).toDouble())
            if(r>radius-1.5) continue
            val angle=atan2(z.toDouble(),x.toDouble())
            val folded=abs(sin(angle*3))
            val petal=radius*(0.28+0.52*folded)
            val band=floor(r/2.6).toInt()
            val seam=abs(r-petal)<0.35 || abs(r-radius*0.88)<0.3
            val spokes=abs(sin(angle*6))*r<0.22
            val material=when {
                r<1.5 -> Material.AMETHYST_BLOCK
                seam -> Material.SEA_LANTERN
                spokes -> Material.POLISHED_DIORITE
                r<petal && band%2==1 -> tiles[(band+floor(folded*3).toInt())%tiles.size]
                band%2==0 -> Material.BLACK_STAINED_GLASS
                else -> Material.POLISHED_BLACKSTONE
            }
            add(DomainBlock(x,-1,z,material))
        }
        for(y in 4..min(13,radius-3)) {
            val r=sqrt(((radius-2.0).pow(2)-y*y).coerceAtLeast(0.0))
            repeat(12) { wedge ->
                val angle=wedge*PI/6
                val width=if(y==4 || y==13) 0 else 1
                for(side in -width..width) {
                    val x=(cos(angle)*r-sin(angle)*side).roundToInt()
                    val z=(sin(angle)*r+cos(angle)*side).roundToInt()
                    val frame=side!=0 || y==4 || y==13
                    val material=if(frame) {
                        if(y%4==0) Material.SEA_LANTERN else Material.POLISHED_DIORITE
                    } else glass[(wedge%6+y/3)%7]
                    add(DomainBlock(x,y,z,material))
                }
            }
        }
        val ceiling=(radius*0.7).toInt()
        val width=(radius*0.43).toInt()
        for(x in -width..width) for(z in -width..width) {
            val r=sqrt((x*x+z*z).toDouble())
            val angle=atan2(z.toDouble(),x.toDouble())
            val petal=width*(0.64+0.36*abs(cos(angle*3)))
            if(r>petal || r<width*0.35) continue
            val outline=abs(r-petal)<0.8 || abs(sin(angle*6))*r<0.4
            add(DomainBlock(x,ceiling,z,if(outline) Material.SEA_LANTERN else glass[(floor(r).toInt()+2)%7]))
        }
    }.filter { it.x*it.x + it.y*it.y + it.z*it.z < (radius-1)*(radius-1) }
        .distinctBy { Triple(it.x,it.y,it.z) }
}
