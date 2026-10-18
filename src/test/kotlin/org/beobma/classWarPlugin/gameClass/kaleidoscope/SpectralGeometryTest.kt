package org.beobma.classWarPlugin.gameClass.kaleidoscope

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.growth.*
import org.bukkit.Material
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.test.*

class SpectralGeometryTest {
    @Test fun `prisms link at eight blocks but not beyond or to themselves`() {
        assertEquals(listOf(0 to 1), SpectralGeometry.edges(listOf(Vector(),Vector(8,0,0),Vector(16.01,0.0,0.0))))
    }
    @Test fun `cyclic networks propagate only once per prism and exclude disconnected prisms`() {
        val points = listOf(Vector(),Vector(6,0,0),Vector(3,0,5),Vector(40,0,0))
        val arrivals = SpectralGeometry.arrivals(points, Vector())
        assertEquals(setOf(0,1,2), arrivals.keys)
        assertEquals(0,arrivals[0]); assertEquals(3,arrivals[1]); assertEquals(3,arrivals[2])
    }
    @Test fun `multiple initial prisms use shortest light path without duplicate emission`() {
        val points=(0..5).map { Vector(it*6,0,0) }
        val result=SpectralGeometry.arrivals(points, Vector(3,0,0))
        assertEquals(listOf(0,0,3,6,9,12),points.indices.map { result[it] })
        assertTrue(SpectralGeometry.arrivals(points,Vector(0,20,0)).isEmpty())
    }
    @Test fun `domain lattice is connected bounded and covers multiple vertical layers`() {
        val nodes=SpectralGeometry.lattice(20)
        assertTrue(nodes.size in 80..180)
        assertEquals(nodes.size,SpectralGeometry.arrivals(nodes,Vector()).size)
        assertTrue(nodes.all { it.length() < 19 })
        assertTrue(nodes.map { it.y }.distinct().size >= 3)
    }
    @Test fun `wave checks entity bounding boxes and does not hit behind its passed front`() {
        val box=BoundingBox(3.8,0.0,-0.3,4.4,1.8,0.3)
        assertTrue(SpectralGeometry.waveTouches(box,Vector(),3.5,4.0))
        assertFalse(SpectralGeometry.waveTouches(box,Vector(),0.0,3.5))
        assertFalse(SpectralGeometry.waveTouches(BoundingBox(-0.1,0.0,-0.1,0.1,0.2,0.1),Vector(),2.0,2.5))
    }
    @Test fun `rosette retains six fold symmetry while rotating`() {
        val points=SpectralGeometry.rosette(17.5,0.2,0.37)
        for(i in 0 until 120) {
            val rotated=points[i].clone().rotateAroundY(-PI/3)
            assertTrue(rotated.distance(points[i+24]) < 1e-8)
        }
    }
    @Test fun `mosaic and facets fit domain terrain constraints and do not obstruct combat plane`() {
        val blocks=SpectralGeometry.interior(20)
        assertTrue(blocks.size in 1000..8192)
        assertTrue(blocks.all { it.y in -1..18 && it.x*it.x+it.y*it.y+it.z*it.z < 19*19 })
        assertTrue(blocks.none { it.y in 0..3 })
        assertEquals(blocks.size, blocks.map { Triple(it.x,it.y,it.z) }.distinct().size)
    }
    @Test fun `new classes are enabled have real skill IDs and growth profiles`() {
        for(id in listOf("bomb-maniac")) {
            assertTrue(id in AbilityCatalog.enabledClassIds())
            val clazz=AbilityCatalog.create(id)
            assertTrue(clazz.skills.all { it.definitionId.startsWith("$id/") })
            assertEquals(clazz.skills.size,clazz.skills.map { it.definitionId }.distinct().size)
            assertEquals(GrowthCombatStyle.ABILITY,GrowthClassCatalog.styles[id])
            assertEquals(GrowthStat.INTELLIGENCE,GrowthProfile.forClass(id).primary)
        }
        val kaleidoscope=org.beobma.classWarPlugin.gameClass.list.Kaleidoskop()
        assertEquals(Rank.SPECIAL,kaleidoscope.rank)
        assertEquals(Material.END_CRYSTAL,kaleidoscope.skills.single { it.isDomainExpansion }.itemMaterial)
        assertEquals(Rank.A,AbilityCatalog.create("bomb-maniac").rank)
        assertEquals(14,SpectralGeometry.WARNING_TICKS)
        assertEquals(7,SpectralGeometry.PRISM_LIMIT)
    }
    @Test fun `parked kaleidoscope cannot be selected or reenabled by configuration`() {
        val id = "kaleidoskop"
        val clazz = org.beobma.classWarPlugin.gameClass.list.Kaleidoskop()
        assertTrue(AbilityCatalog.isDeferred(id))
        assertFalse(id in AbilityCatalog.enabledClassIds())
        assertFailsWith<IllegalArgumentException> { AbilityCatalog.create(id) }
        val balances = org.beobma.classWarPlugin.manager.ClassBalanceManager
        assertFalse(balances.isEnabled(clazz))
        balances.toggleEnabled(clazz)
        balances.adjust(clazz, org.beobma.classWarPlugin.manager.ClassBalanceField.DAMAGE, true, 1)
        balances.reset(clazz)
        assertFalse(balances.isEnabled(clazz))
        val config = org.bukkit.configuration.file.YamlConfiguration()
        config.set("growth.classes.$id.primary", "STRENGTH")
        assertFalse(id in org.beobma.classWarPlugin.growth.GrowthSettings.read(config).profiles)
        assertFalse(id in GrowthClassCatalog.styles)
    }
}
