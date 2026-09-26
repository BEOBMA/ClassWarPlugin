package org.beobma.classWarPlugin.gameClass.relic

import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.test.Test
import kotlin.test.assertEquals

class SpearThrowDamageTest {
    @Test fun `close throws deal half damage including exactly four blocks`() {
        val origin = Vector(0.0,1.6,0.0)
        for (distance in listOf(0.0,1.0,3.999,4.0)) {
            assertEquals(3.0,SpearThrowDamage.calculate(origin,BoundingBox(distance,0.0,-0.3,distance+0.6,1.8,0.3)))
        }
        assertEquals(6.0,SpearThrowDamage.calculate(origin,BoundingBox(4.001,0.0,-0.3,4.601,1.8,0.3)))
    }

    @Test fun `range uses nearest bounding box point in three dimensions`() {
        assertEquals(3.0,SpearThrowDamage.calculate(Vector(),BoundingBox(0.0,4.0,0.0,1.0,6.0,1.0)))
        assertEquals(6.0,SpearThrowDamage.calculate(Vector(),BoundingBox(3.0,3.0,0.0,4.0,4.0,1.0)))
    }
}
