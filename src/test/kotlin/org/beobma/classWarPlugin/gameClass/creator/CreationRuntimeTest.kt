package org.beobma.classWarPlugin.gameClass.creator

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.gameClass.Rank
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.test.*

class CreationRuntimeTest {
    @Test fun `creation seals have a constant budget and stay perpendicular to aim`() {
        for (normal in listOf(Vector(), Vector(0.0,1.0,0.0), Vector(0.0,-1.0,0.0), Vector(1.0,2.0,3.0))) {
            val original = normal.clone()
            val points = CreationGeometry.seal(3.0, normal)
            assertEquals(30, points.size)
            assertEquals(original, normal)
            points.forEach {
                assertTrue(it.x.isFinite() && it.y.isFinite() && it.z.isFinite())
                assertTrue(it.length() <= 3.0 + 1e-8)
                assertEquals(0.0, it.dot(normal), 1e-8)
            }
        }
    }
    @Test fun `chain inclination is bounded and normal vertical drops remain available`() {
        assertEquals(Vector(0.0, 24.0, 0.0), ChainVisuals.spawnOffset(24.0, 0.0, 0.0))
        for (i in 0..36) {
            val offset = ChainVisuals.spawnOffset(24.0, 0.28, i * Math.PI / 18)
            assertEquals(24.0, offset.y)
            assertEquals(6.72, kotlin.math.hypot(offset.x, offset.z), 1e-8)
        }
        assertEquals(ChainVisuals.damage(false) * 0.1, ChainVisuals.damage(true), 1e-8)
    }
    @Test fun `creation hall is bounded unique walkable and fits interior budget`() {
        val plan = CreationArchitecture.build(25)
        assertTrue(plan.size in 1000..8192)
        assertEquals(plan.size, plan.map { Triple(it.x, it.y, it.z) }.distinct().size)
        assertTrue(plan.all { it.y in -1..24 && it.x*it.x + it.y*it.y + it.z*it.z < 24*24 })
        assertTrue(plan.none { it.y in 0..3 && it.x*it.x + it.z*it.z < 12*12 })
        assertTrue(plan.any { it.y >= 19 })
        assertTrue(plan.any { it.material == org.bukkit.Material.IRON_CHAIN })
        assertTrue(plan.any { it.material == org.bukkit.Material.SEA_LANTERN })
        assertTrue(plan.any { it.material == org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS && it.y > 15 })
        assertTrue(plan.any { it.material == org.bukkit.Material.QUARTZ_BRICKS })
        assertTrue(AbilityCatalog.create("creator").skills.last().description.any { "50칸" in it })
    }

    @Test fun `infinite mana presentation restores numbers and supports independent owners`() {
        val mana = org.beobma.classWarPlugin.status.list.Mana()
        mana.power = 73
        val original = mana.actionBarText()
        val first = mana.displayInfinite(Any())
        val second = mana.displayInfinite(Any())
        assertFalse(mana.actionBarText().contains("73"))
        assertTrue(mana.actionBarText().contains("∞"))
        first.close()
        assertFalse(mana.actionBarText().contains("73"))
        second.close()
        assertEquals(original, mana.actionBarText())
        first.close()
        assertEquals(73, mana.power)
    }

    @Test fun `creator registers four distinct skills including destruction`() {
        val creator = AbilityCatalog.create("creator")
        assertEquals(Rank.SPECIAL, creator.rank)
        assertEquals(listOf("creator/red-skill", "creator/orange-skill", "creator/yellow-skill", "creator/domain-skill"), creator.skills.map { it.definitionId })
        assertTrue("creator" in AbilityCatalog.enabledClassIds())
    }

    @Test fun `five chain bursts within five seconds snare once and reset the combo`() {
        val combo = ChainCombo()
        val target = UUID.randomUUID()
        repeat(4) { assertFalse(combo.hit(target, it * 25L)) }
        assertTrue(combo.hit(target, 100))
        assertFalse(combo.hit(target, 101))
    }

    @Test fun `expired hits and other targets never contribute to a snare`() {
        val combo = ChainCombo()
        val a = UUID.randomUUID(); val b = UUID.randomUUID()
        repeat(4) { assertFalse(combo.hit(a, 0)) }
        assertFalse(combo.hit(b, 50))
        assertFalse(combo.hit(a, 101))
        repeat(3) { assertFalse(combo.hit(a, 102)) }
        assertTrue(combo.hit(a, 103))
    }

    @Test fun `spear sweep hits a narrow box between tick positions`() {
        val box = BoundingBox(1.2, 0.0, -0.3, 1.8, 1.8, 0.3)
        val hit = CreationGeometry.contact(box, Vector(0.0, 1.0, 0.0), Vector(2.7, 0.0, 0.0), 2.7, 0.16)
        assertNotNull(hit)
        assertEquals(1.04, hit.x, 1e-8)
        assertNull(CreationGeometry.contact(box, Vector(0.0, 1.0, 0.0), Vector(1.0, 0.0, 0.0), 0.8, 0.16))
        assertNull(CreationGeometry.contact(box, Vector(0.0, 4.0, 0.0), Vector(1.0, 0.0, 0.0), 2.7, 0.16))
    }

    @Test fun `overlap at spawn counts and querying never changes the entity box`() {
        val box = BoundingBox(0.0, 0.0, 0.0, 1.0, 2.0, 1.0)
        val original = box.clone()
        val point = Vector(0.5, 1.0, 0.5)
        assertEquals(point, CreationGeometry.contact(box, point, Vector(), 0.0, 0.5))
        assertEquals(original, box)
    }

    @Test fun `burst radius measures nearest bounding box surface not feet`() {
        val tall = BoundingBox(2.9, 0.0, -0.5, 3.9, 4.0, 0.5)
        assertTrue(CreationGeometry.inRadius(tall, Vector(0.0, 3.0, 0.0), 3.0))
        assertFalse(CreationGeometry.inRadius(tall, Vector(0.0, 3.0, 0.0), 2.8))
    }
}
