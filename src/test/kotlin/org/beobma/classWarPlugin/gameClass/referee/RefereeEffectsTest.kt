package org.beobma.classWarPlugin.gameClass.referee

import org.beobma.classWarPlugin.domain.DomainInteriors
import org.bukkit.Material
import kotlin.math.abs
import kotlin.test.*

class RefereeEffectsTest {
    @Test fun `seal closes symmetrically and has a fixed particle budget`() {
        for (radius in listOf(0.0, 0.3, 1.8, 8.0)) {
            val points = CourtGlyphs.seal(radius)
            assertEquals(66, points.size)
            assertTrue(points.all { abs(it.x) + abs(it.z) <= radius + 1e-8 && it.y == 0.12 })
            assertTrue(abs(points.sumOf { it.x }) < 1e-8)
            assertTrue(abs(points.sumOf { it.z }) < 1e-8)
        }
    }

    @Test fun `moving scales are bounded and stay in front of the courtroom bench`() {
        val level = CourtGlyphs.scales(0.0)
        assertNotEquals(level, CourtGlyphs.scales(0.15))
        assertEquals(CourtGlyphs.scales(0.3), CourtGlyphs.scales(10.0))
        for (tilt in listOf(-0.3, -0.1, 0.0, 0.1, 0.3)) {
            val points = CourtGlyphs.scales(tilt)
            assertTrue(points.size <= 130)
            assertEquals(level.size, points.size)
            assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.z == -2.5 })
            assertTrue(points.all { abs(it.x) <= 3.5 && it.y in 2.0..6.0 })
        }
    }

    @Test fun `detailed courtroom fits bounds and keeps trial positions and aisle clear`() {
        for (radius in 4..24) {
            val replacement = RefereeEffects.courtroom(radius)
            assertTrue(replacement.size <= 8192)
            assertEquals(replacement.size, replacement.map { Triple(it.x, it.y, it.z) }.distinct().size)
            assertTrue(replacement.all { it.y in -1 until radius && it.x * it.x + it.y * it.y + it.z * it.z < (radius - 1) * (radius - 1) })
            assertTrue(replacement.none { it.y in 0..2 && abs(it.x) <= 1 && it.z in -3..radius })
            val positions = replacement.associateBy { Triple(it.x, it.y, it.z) }
            replacement.forEach { assertEquals(it.material, positions[Triple(-it.x, it.y, it.z)]?.material) }
        }
        val court = RefereeEffects.courtroom(10)
        assertTrue(court.size > DomainInteriors.courtroom(10).size * 4)
        assertTrue(court.any { it.y >= 6 })
        assertTrue(court.any { it.material == Material.IRON_CHAIN })
        assertTrue(court.any { it.y == -1 && it.material == Material.RED_NETHER_BRICKS })
    }

    @Test fun `stroke includes both endpoints without nonfinite coordinates`() {
        val start = CourtGlyphs.Point(-1.0, 0.0, 2.0)
        val end = CourtGlyphs.Point(2.0, 3.0, -1.0)
        val stroke = CourtGlyphs.line(start, end)
        assertEquals(start, stroke.first())
        assertEquals(end, stroke.last())
        assertEquals(13, stroke.size)
    }
}
