package org.beobma.classWarPlugin.gameClass.snakesword

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.gameClass.Rank
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.test.*

class SnakeSwordGeometryTest {
    @Test fun `repeated damage halves with a one point floor`() {
        assertEquals(listOf(6.0, 3.0, 1.5, 1.0, 1.0), (0..4).map { SnakeSwordGeometry.repeatDamage(6.0, it) })
        assertEquals(1.0, SnakeSwordGeometry.repeatDamage(5.0, Int.MAX_VALUE))
    }
    @Test fun `stationary extended blade cannot deal damage and speed caps at five`() {
        assertEquals(0.0, SnakeSwordGeometry.speedDamage(0.0))
        assertEquals(0.0, SnakeSwordGeometry.speedDamage(Double.NaN))
        assertEquals(2.0, SnakeSwordGeometry.speedDamage(0.5))
        assertEquals(5.0, SnakeSwordGeometry.speedDamage(10.0))
    }
    @Test fun `contact uses the target bounding box and the whole swept blade`() {
        val before = listOf(Vector(-3, 1, 0), Vector(-3, 1, 6))
        val after = listOf(Vector(3, 1, 0), Vector(3, 1, 6))
        assertNotNull(SnakeSwordGeometry.contact(BoundingBox(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6), before, after))
        assertNull(SnakeSwordGeometry.contact(BoundingBox(8.0, 0.0, 2.0, 8.6, 1.8, 2.6), before, after))
        assertNull(SnakeSwordGeometry.contact(BoundingBox(-0.3, 4.0, 2.0, 0.3, 5.8, 2.6), before, after))
    }
    @Test fun `blade remains finite looking straight up or down`() {
        listOf(Vector(0, 1, 0), Vector(0, -1, 0)).forEach {
            val blade = SnakeSwordGeometry.blade(Vector(), it, 12.0, 0.8)
            assertEquals(17, blade.size)
            blade.forEach { point -> assertTrue(point.x.isFinite() && point.y.isFinite() && point.z.isFinite()) }
            assertEquals(12.0, blade.last().length(), 0.00001)
        }
    }
    @Test fun `snake sword is enabled at rank A with only extend`() {
        assertTrue("snake-sword" in AbilityCatalog.enabledClassIds())
        val gameClass = AbilityCatalog.create("snake-sword")
        assertEquals(Rank.A, gameClass.rank)
        assertEquals(listOf("snake-sword/extend"), gameClass.skills.map { it.definitionId })
    }
}
