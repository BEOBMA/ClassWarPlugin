package org.beobma.classWarPlugin.gameClass.kaleidoscope

import kotlin.test.*

class SpectralCombatVisualsTest {
    @Test fun `every wave receives a bounded allocation without starving late indices`() {
        for(count in listOf(0,1,7,32,33,64,129,256)) {
            val seen=mutableSetOf<Int>()
            repeat(8) { frame ->
                val allocated=SpectralCombatVisuals.waveAllocation(count,frame)
                assertTrue(allocated.values.sum() <= SpectralCombatVisuals.WAVE_BUDGET)
                assertTrue(allocated.keys.all { it in 0 until count })
                assertTrue(allocated.values.all { it in 16..64 })
                seen+=allocated.keys
            }
            assertEquals((0 until count).toSet(),seen)
        }
    }
    @Test fun `normal seven prisms all receive complete sixty four point waves`() {
        assertEquals(List(7) { 64 },SpectralCombatVisuals.waveAllocation(7,9).values.toList())
    }
    @Test fun `wave rings stay on the damage sphere and include three dimensional samples`() {
        for(count in listOf(16,24,64)) {
            val points=SpectralCombatVisuals.rings(4.0,0.65,count)
            assertEquals(count,points.size)
            points.forEach { assertEquals(4.0,it.length(),1e-8) }
            assertTrue(points.any { it.y > 1 })
            assertTrue(points.any { it.y < -1 })
        }
    }
    @Test fun `beam starts strong and fades out without changing impact delay`() {
        assertEquals(14,SpectralGeometry.WARNING_TICKS)
        assertEquals(1.0,SpectralCombatVisuals.beamStrength(0))
        assertEquals(1.0,SpectralCombatVisuals.beamStrength(3))
        assertTrue(SpectralCombatVisuals.beamStrength(12)<SpectralCombatVisuals.beamStrength(6))
        assertEquals(0.0,SpectralCombatVisuals.beamStrength(SpectralCombatVisuals.AFTERGLOW_TICKS))
        assertEquals(0.0,SpectralCombatVisuals.beamStrength(-1))
    }
}
