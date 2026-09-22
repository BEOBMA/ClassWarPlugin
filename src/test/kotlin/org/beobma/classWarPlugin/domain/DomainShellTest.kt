package org.beobma.classWarPlugin.domain

import org.beobma.classWarPlugin.status.list.DomainDurationStatus
import kotlin.math.*
import kotlin.test.*

class DomainShellTest {
    @Test fun `clearing covers whole sphere without touching shell or exterior`() {
        for (radius in listOf(4, 10, 24)) {
            val interior = DomainShell.interiorCells(radius).toSet()
            val shell = DomainShell.cells(radius).toSet()
            assertTrue(interior.any { it.y < -1 })
            assertTrue(DomainShell.Cell(radius - 1, 0, 0) in interior)
            assertTrue(interior.intersect(shell).isEmpty())
            for (y in -radius - 2..radius + 2) for (x in -radius - 2..radius + 2) for (z in -radius - 2..radius + 2) {
                val distance = x * x + (y + 0.5) * (y + 0.5) + z * z
                assertEquals(distance < radius * radius, DomainShell.Cell(x, y, z) in interior)
            }
        }
    }

    @Test fun `shell remains outside terrain clearing region and within protection bounds`() {
        for (radius in 4..24) {
            val cells = DomainShell.cells(radius)
            assertEquals(cells.size, cells.distinct().size)
            assertTrue(cells.zipWithNext().all { (a, b) -> a.y <= b.y })
            assertTrue(cells.size < 18000, "radius=$radius cells=${cells.size}")
            cells.forEach {
                assertTrue(abs(it.x) <= radius + 2 && abs(it.z) <= radius + 2 && it.y in (-radius - 2)..(radius + 2))
                if (it.y >= 0) assertTrue(it.x * it.x + it.y * it.y + it.z * it.z >= (radius - 1) * (radius - 1))
            }
        }
    }

    @Test fun `rays from chamber hit real sphere blocks above and below the court`() {
        for (radius in listOf(4, 10, 24)) {
            val cells = DomainShell.cells(radius).toSet()
            for (azimuth in 0 until 360 step 5) for (elevation in -90..90 step 5) {
                val a = Math.toRadians(azimuth.toDouble())
                val e = Math.toRadians(elevation.toDouble())
                val hit = (0..((radius + 3) * 20)).any { sample ->
                    val length = sample / 20.0
                    val x = floor(0.5 + cos(a) * cos(e) * length).toInt()
                    val z = floor(0.5 + sin(a) * cos(e) * length).toInt()
                    val y = floor(sin(e) * length).toInt()
                    DomainShell.Cell(x, y, z) in cells
                }
                assertTrue(hit, "Unsealed ray: radius=$radius azimuth=$azimuth elevation=$elevation")
            }
        }
    }

    @Test fun `sphere has matching underside and complete formation and dissolution sweep`() {
        for (radius in listOf(4, 10, 24)) {
            val cells = DomainShell.cells(radius).toSet()
            assertTrue(cells.any { it.y < -1 })
            cells.forEach {
                assertTrue(DomainShell.Cell(it.x, -it.y - 1, it.z) in cells)
                assertTrue(it.y >= DomainShell.frontHeight(radius, 0.0))
                assertTrue(it.y <= DomainShell.frontHeight(radius, 1.0))
            }
        }
    }

    @Test fun `airborne court has centered floor and rejects clipped world height`() {
        val floor = DomainShell.floorCells(10)
        assertTrue(DomainShell.Cell(0, -1, 0) in floor)
        assertTrue(DomainShell.Cell(9, -1, 0) in floor)
        assertTrue(floor.all { it.y == -1 && it.x * it.x + it.z * it.z < 100 })
        assertTrue(DomainShell.fitsHeight(120, 10, -64, 320))
        assertTrue(DomainShell.fitsHeight(-52, 10, -64, 320))
        assertFalse(DomainShell.fitsHeight(-53, 10, -64, 320))
        assertTrue(DomainShell.fitsHeight(307, 10, -64, 320))
        assertFalse(DomainShell.fitsHeight(308, 10, -64, 320))
    }

    @Test fun `longer title keeps subtitle and combat start separated`() {
        val timeline = DomainTimeline(0, 600, 2600)
        timeline.beginTitle(3_000_000_000)
        assertTrue(timeline.subtitleVisible(3_600_000_000))
        assertFalse(timeline.fightStarted(4_800_000_000))
        assertFalse(timeline.fightStarted(5_599_000_000))
        assertTrue(timeline.fightStarted(5_600_000_000))
    }

    @Test fun `restoration captures original block and contents only once`() {
        data class State(val material: String, val contents: List<String>)
        var current = State("chest", listOf("book", "sword"))
        val original = current
        val ledger = DomainBlockLedger<String, State> { current = it; true }
        ledger.capture("wall") { current }
        current = State("black_concrete", emptyList())
        ledger.capture("wall") { current }
        current = State("air", emptyList())
        ledger.restore("wall")
        assertEquals(original, current)
        assertTrue(ledger.keys().isEmpty())
        ledger.restore("wall") // Natural dissolution followed by session cleanup is idempotent.
        assertEquals(original, current)
    }

    @Test fun `failed restoration keeps evidence for cleanup retry`() {
        var fail = true
        var restored = ""
        val ledger = DomainBlockLedger<Int, String> { if (fail) false else { restored = it; true } }
        ledger.capture(1) { "original" }
        assertFailsWith<IllegalStateException> { ledger.restore(1) }
        assertEquals(listOf(1), ledger.keys())
        fail = false
        ledger.restore(1)
        assertEquals("original", restored)
        assertTrue(ledger.keys().isEmpty())
    }

    @Test fun `domain status uses one session clock and shows rounded remaining seconds`() {
        val status = DomainDurationStatus()
        assertTrue(status.isClassMechanic)
        assertFalse(status.isHarmful)
        assertNull(status.duration) // No competing status-manager timer.
        assertTrue("전개 중" in status.actionBarText())
        assertTrue(status.synchronize(false, false, 400))
        assertTrue("20초" in status.actionBarText())
        assertFalse(status.synchronize(false, false, 399))
        assertTrue(status.synchronize(false, false, 380))
        assertTrue("19초" in status.actionBarText())
        assertTrue(status.synchronize(false, false, 1))
        assertTrue("1초" in status.actionBarText())
        assertTrue(status.synchronize(false, true, 0))
        assertTrue("해제 중" in status.actionBarText())
        assertFalse(status.synchronize(false, true, 0))
    }
}
