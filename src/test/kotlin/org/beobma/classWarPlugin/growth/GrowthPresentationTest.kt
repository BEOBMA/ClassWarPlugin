package org.beobma.classWarPlugin.growth

import org.bukkit.configuration.file.YamlConfiguration
import kotlin.random.Random
import kotlin.test.*

class GrowthPresentationTest {
    @Test fun `experience bar follows partial gains rollovers and maximum level`() {
        val settings = GrowthSettings(maximumLevel = 3)
        val state = GrowthPlayerState()
        assertEquals(0f, state.experienceProgress(settings))
        state.gain(30, settings)
        assertEquals(0.5f, state.experienceProgress(settings))
        state.gain(30, settings)
        assertEquals(2, state.level)
        assertEquals(0f, state.experienceProgress(settings))
        state.gain(85, settings)
        assertEquals(3, state.level)
        assertEquals(1f, state.experienceProgress(settings))
        state.gain(10000, settings)
        assertEquals(1f, state.experienceProgress(settings))
    }

    @Test fun `token chooses hotbar first then storage and never overwrites full inventory`() {
        assertEquals(0, GrowthControls.firstFreeSlot { true })
        assertEquals(8, GrowthControls.firstFreeSlot { it >= 8 })
        assertEquals(9, GrowthControls.firstFreeSlot { it >= 9 })
        assertEquals(35, GrowthControls.firstFreeSlot { it == 35 })
        assertNull(GrowthControls.firstFreeSlot { false })
    }

    @Test fun `equipment list includes only owned valid ids in stable order`() {
        assertTrue(GrowthItems.owned(emptySet()).isEmpty())
        val state = GrowthPlayerState()
        state.inventory.addAll(listOf("moon-heart", "blood-fang", "unknown"))
        val visible = GrowthItems.owned(state.inventory)
        assertEquals(listOf("blood-fang", "moon-heart"), visible.map { it.id })
        assertTrue(state.equip(visible[0].id))
        assertEquals("blood-fang", state.equipment[GrowthSlot.WEAPON])
        assertTrue(state.equip(visible[1].id))
        assertEquals("moon-heart", state.equipment[GrowthSlot.RELIC])
    }

    @Test fun `monster health labels default on and can be explicitly disabled`() {
        val config = YamlConfiguration()
        assertTrue(GrowthSettings().mobHealthVisible)
        assertTrue(GrowthSettings.read(config).mobHealthVisible)
        config.set("growth.mobs.show-health", false)
        assertFalse(GrowthSettings.read(config).mobHealthVisible)
        config.set("growth.mobs.show-health", true)
        assertTrue(GrowthSettings.read(config).mobHealthVisible)
    }

    @Test fun `all thirty two regions can have unique nonnumeric proper names in the same biome`() {
        for (terrain in listOf("forest", "beach", "mountain", "snow", "desert", "ruins", "plains")) {
            val names = RegionNames(Random(12))
            val allocated = List(32) { names.next(terrain) }
            assertEquals(32, allocated.toSet().size)
            assertFalse(allocated.any { name -> name.any(Char::isDigit) })
            assertEquals(32, allocated.map { it.substringBefore(' ') }.toSet().size)
        }
    }

    @Test fun `names are biome sensitive and deterministic for a seed`() {
        val first = RegionNames(Random(123))
        val second = RegionNames(Random(123))
        val terrain = listOf("forest", "beach", "mountain", "snow", "desert", "ruins", "plains")
        val a = terrain.map(first::next)
        assertEquals(a, terrain.map(second::next))
        assertTrue(a[0].endsWith("숲"))
        assertTrue(a[1].endsWith("해변"))
        assertEquals(a.size, a.map { it.substringBefore(' ') }.toSet().size)
    }

    @Test fun `map border has a white core with dark halos on both sides`() {
        val labels = IntArray(7 * 5) { if (it % 7 < 3) 0 else 1 }
        val mask = RegionMapBorders.mask(labels, 7, 5)
        for (y in 0..4) assertContentEquals(intArrayOf(0, 1, 2, 1, 0, 0, 0), mask.copyOfRange(y * 7, y * 7 + 7))
    }

    @Test fun `map border does not invent edges in one region or outside the map`() {
        assertTrue(RegionMapBorders.mask(IntArray(25), 5, 5).all { it == 0 })
        assertTrue(RegionMapBorders.mask(IntArray(25) { -1 }, 5, 5).all { it == 0 })
        val labels = IntArray(25) { if (it % 5 == 0) -1 else if (it % 5 < 3) 0 else 1 }
        val mask = RegionMapBorders.mask(labels, 5, 5)
        for (y in 0..4) assertEquals(0, mask[y * 5])
    }

    private fun splitMap(): RegionLayout {
        val surface = SurfaceSnapshot(-4, -4, 4, IntArray(16) { 65 }, Array(16) { "plains" })
        val regions = (0..1).map { id ->
            GrowthRegion(id, listOf(RegionRect(id * 2, 0, 2, 4)), "region", "plains", intArrayOf(), setOf(1 - id), null)
        }
        return RegionLayout(surface, regions, IntArray(16) { if (it % 4 < 2) 0 else 1 }, 0)
    }

    @Test fun `world particles follow only interregion seams at actual world coordinates`() {
        val boundaries = RegionBoundaries(splitMap())
        assertEquals(4, boundaries.points.size)
        assertTrue(boundaries.points.all { it.x == -2.0 && it.y == 65 && it.first == 0 && it.second == 1 })
        assertEquals(listOf(-3.5, -2.5, -1.5, -0.5), boundaries.points.map { it.z })
        assertTrue(boundaries.near(100.0, 100.0).isEmpty())
        assertEquals(1, boundaries.near(-2.0, -3.5, radius = 0.2).size)
        assertEquals(2, boundaries.near(-2.0, -3.5, limit = 2).size)
    }

    @Test fun `seam color follows most dangerous adjacent state without regenerating geometry`() {
        val map = splitMap()
        val point = RegionBoundaries(map).points.first()
        assertEquals(RegionState.SAFE, point.state(map))
        map.regions[0].state = RegionState.WARNING
        assertEquals(RegionState.WARNING, point.state(map))
        map.regions[1].state = RegionState.FORBIDDEN
        assertEquals(RegionState.FORBIDDEN, point.state(map))
    }

    @Test fun `blocked seam height uses neighboring ground or a viewer height fallback`() {
        val map = splitMap()
        map.surface.heights[1] = SurfaceSnapshot.BLOCKED
        assertEquals(65, RegionBoundaries(map).points.first().y)
        map.surface.heights[2] = SurfaceSnapshot.BLOCKED
        assertNull(RegionBoundaries(map).points.first().y)
    }
}
