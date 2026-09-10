package org.beobma.classWarPlugin.gameClass.warCorrespondent

import org.bukkit.Location
import kotlin.math.*
import kotlin.test.*

class RecordingGeometryTest {
    @Test fun `sector detects partial bounding box overlap without extending its radius or angle`() {
        val geometry = RecordingGeometry(0f, 80f)
        // The entity center is outside, but the near edge is inside the radius.
        assertTrue(geometry.intersects(-0.3, 15.9, 0.3, 16.5))
        assertFalse(geometry.intersects(-0.3, 16.01, 0.3, 16.5))
        assertTrue(geometry.intersects(8.5, 4.8, 9.1, 5.4))
        assertFalse(geometry.intersects(9.5, 4.0, 10.0, 4.5))
        assertFalse(geometry.intersects(-0.3, -2.0, 0.3, -1.0))
        assertTrue(geometry.intersects(-0.3, -0.3, 0.3, 0.3))
        // A wide box can cross the sector even when none of its corners lie in it.
        assertTrue(geometry.intersects(-20.0, 5.0, 20.0, 6.0))
        val west = RecordingGeometry(90f, -80f)
        assertTrue(west.intersects(-16.3, -0.3, -15.9, 0.3))
        assertFalse(west.intersects(1.0, -0.3, 2.0, 0.3))
    }

    @Test fun `the fixed sector includes its edges but excludes behind and beyond maximum range`() {
        val geometry = RecordingGeometry(0f, 80f)
        assertTrue(geometry.contains(0.0, 16.0))
        assertTrue(geometry.contains(sin(PI / 3) * 16, cos(PI / 3) * 16))
        assertTrue(geometry.contains(0.0, 0.0))
        assertFalse(geometry.contains(0.0, 16.01))
        assertFalse(geometry.contains(0.0, -1.0))
        assertFalse(geometry.contains(sin(PI / 3 + 0.01) * 10, cos(PI / 3 + 0.01) * 10))
        val west = RecordingGeometry(90f, -60f)
        assertTrue(west.contains(-16.0, 0.0))
        assertFalse(west.contains(16.0, 0.0))
    }

    @Test fun `grid fills the sector and screen matches the targeting limit at every heading`() {
        for (yaw in listOf(0f, 30f, 90f, 180f, -150f)) {
            val geometry = RecordingGeometry(yaw, 0f)
            assertTrue(geometry.floorGrid.size > 400)
            assertTrue(geometry.floorGrid.all { geometry.contains(it.x, it.z) })
            assertTrue(geometry.floorGrid.any { hypot(it.x, it.z) < 2.0 })
            assertTrue(geometry.floorGrid.any { hypot(it.x, it.z) in 7.9..8.1 })
            for (point in geometry.screenGrid + geometry.screenBorder) {
                assertEquals(16.0, hypot(point.x, point.z), 1e-8)
                assertTrue(geometry.contains(point.x, point.z))
            }
            assertEquals(4.15, geometry.screenGrid.maxOf { it.y }, 1e-8)
            assertTrue(geometry.floorGrid.size + geometry.screenGrid.size + geometry.screenBorder.size < 900)
        }
    }

    @Test fun `view locking preserves movement and height without mutating the original event destination`() {
        val geometry = RecordingGeometry(35f, -15f)
        val destination = Location(null, 100.0, 73.0, -42.0, 160f, 50f)
        val locked = geometry.lockView(destination)
        assertEquals(destination.toVector(), locked.toVector())
        assertEquals(35f, locked.yaw)
        assertEquals(-15f, locked.pitch)
        assertEquals(160f, destination.yaw)
        assertEquals(50f, destination.pitch)
    }

    @Test fun `camera item cooldown matches exposure with one and three second limits`() {
        assertEquals(20, RecordingGeometry.cameraCooldownTicks(0))
        assertEquals(20, RecordingGeometry.cameraCooldownTicks(19))
        assertEquals(37, RecordingGeometry.cameraCooldownTicks(37))
        assertEquals(60, RecordingGeometry.cameraCooldownTicks(60))
        assertEquals(60, RecordingGeometry.cameraCooldownTicks(80))
    }
}
