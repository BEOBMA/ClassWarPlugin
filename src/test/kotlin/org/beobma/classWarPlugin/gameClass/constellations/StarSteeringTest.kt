package org.beobma.classWarPlugin.gameClass.constellations

import org.bukkit.util.Vector
import kotlin.math.acos
import kotlin.test.*

class StarSteeringTest {
    @Test fun `homing turns gradually and retains unit length`() {
        val start = Vector(0.0,-1.0,0.0)
        val result = StarSteering.turn(start,Vector(10.0,0.0,0.0),0.018)
        assertEquals(1.0,result.length(),1e-9)
        assertEquals(0.018,acos(start.dot(result)),1e-9)
        assertEquals(Vector(0.0,-1.0,0.0),start)
    }
    @Test fun `domain star can turn around without an instant reversal`() {
        var heading = Vector(1.0,0.0,0.0)
        val goal = Vector(-1.0,0.0,0.0)
        repeat(24) { heading = StarSteering.turn(heading,goal,0.16) }
        assertTrue(heading.dot(goal)>0.999)
    }
}
