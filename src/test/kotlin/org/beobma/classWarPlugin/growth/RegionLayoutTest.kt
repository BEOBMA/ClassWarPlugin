package org.beobma.classWarPlugin.growth

import kotlin.test.*

class RegionLayoutTest {
    private fun flat(size: Int = 128) = SurfaceSnapshot(-64, -64, size, IntArray(size * size) { 65 }, Array(size * size) { "plains" })
    @Test fun `default map and final border can form sixteen connected regions`() {
        val map = RegionGenerator.generate(flat(320), GrowthSettings(), 40, 20260914)
        assertEquals(16, map.regions.size)
        assertTrue(map.connected(map.regions.indices.toSet()))
        assertTrue(map.regions.any { (it.finalSquare?.width ?: 0) >= 40 })
    }
    @Test fun `cancelled generation never publishes a layout`() {
        assertFailsWith<IllegalStateException> { RegionGenerator.generate(flat(), GrowthSettings(), 12, 1) { true } }
    }
    @Test fun `every surface column belongs to exactly one rectilinear region`() {
        val surface = flat()
        val map = RegionGenerator.generate(surface, GrowthSettings(maximumRegions = 16, minimumRegions = 16), 12, 44)
        assertEquals(16, map.regions.size)
        assertTrue(map.regions.any { it.rectangles.size > 1 })
        for (z in 0 until surface.size) for (x in 0 until surface.size) {
            val owners = map.regions.filter { r -> r.rectangles.any { it.contains(x, z) } }
            assertEquals(1, owners.size)
            assertEquals(owners.single().id, map.at(x - 63.5, z - 63.5)!!.id)
        }
        assertNull(map.at(-64.01, 0.0))
        assertNull(map.at(64.0, 0.0))
    }
    @Test fun `warning batches always retain connected safety and a direct escape`() {
        for (seed in 0..19) {
            val map = RegionGenerator.generate(flat(), GrowthSettings(), 12, seed)
            val schedule = RegionSchedule(map, 3, seed)
            var transitions = 0
            while (schedule.finalRegion == null) {
                val safe = map.regions.filter { it.state == RegionState.SAFE }.map { it.id }.toSet()
                assertTrue(map.connected(safe))
                val warning = map.regions.filter { it.state == RegionState.WARNING }
                assertTrue(warning.isNotEmpty())
                warning.forEach { assertTrue(it.neighbors.any { n -> n in safe }) }
                schedule.advance()
                warning.forEach { assertEquals(RegionState.FORBIDDEN, it.state) }
                assertTrue(++transitions < map.regions.size)
            }
            val final = schedule.finalRegion!!
            assertNotNull(final.finalSquare)
            val square = final.finalSquare!!
            for (z in square.z until square.z + square.depth) for (x in square.x until square.x + square.width)
                assertEquals(final.id, map.labels[z * map.surface.size + x])
        }
    }
    @Test fun `world seed and map produce reproducible regions and closure schedules`() {
        val first = RegionGenerator.generate(flat(), GrowthSettings(), 12, 66)
        val second = RegionGenerator.generate(flat(), GrowthSettings(), 12, 66)
        assertContentEquals(first.labels, second.labels)
        val a = RegionSchedule(first, 1, 66); val b = RegionSchedule(second, 1, 66)
        repeat(first.regions.size - 1) {
            assertEquals(first.regions.map { it.state }, second.regions.map { it.state })
            a.advance(); b.advance()
        }
        assertEquals(first.regions.size - 1, a.phaseIndex)
    }
    @Test fun `nonwalkable maps fail within a bounded attempt budget`() {
        val s = flat(64)
        s.heights.fill(SurfaceSnapshot.BLOCKED)
        assertFailsWith<IllegalStateException> { RegionGenerator.generate(s,
            GrowthSettings(maximumRegions = 4, minimumRegions = 2, generationAttempts = 2), 8, 1) }
    }
    @Test fun `cliff and deep water never create walkable portals`() {
        val s = flat(64)
        s.heights[1] = 100
        s.heights[2] = SurfaceSnapshot.BLOCKED
        assertFalse(s.canWalk(0, 1)); assertFalse(s.canWalk(1, 2)); assertFalse(s.canWalk(0, -1))
        s.heights[1] = 66
        assertTrue(s.canWalk(0, 1))
    }
}
