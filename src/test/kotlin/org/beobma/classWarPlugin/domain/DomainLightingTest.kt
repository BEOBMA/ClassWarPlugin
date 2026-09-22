package org.beobma.classWarPlugin.domain

import org.bukkit.Material
import kotlin.test.*

class DomainLightingTest {
    @Test fun `every empty interior cell including head space accepts light`() {
        for (material in listOf(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.LIGHT)) {
            assertTrue(DomainLighting.canFill(10, 0, 0, 0, material))
            assertTrue(DomainLighting.canFill(10, 0, 1, 0, material))
            assertTrue(DomainLighting.canFill(10, 0, 9, 0, material))
            assertTrue(DomainLighting.canFill(10, 9, 0, 0, material))
        }
    }

    @Test fun `lighting preserves shell furniture floor and nonempty terrain`() {
        for (material in listOf(Material.BLACK_CONCRETE, Material.POLISHED_BLACKSTONE,
            Material.POLISHED_DEEPSLATE, Material.CHISELED_POLISHED_BLACKSTONE, Material.CHEST, Material.WATER))
            assertFalse(DomainLighting.canFill(10, 1, 1, 1, material))
        assertTrue(DomainLighting.canFill(10, 0, -1, 0, Material.AIR))
        assertTrue(DomainLighting.canFill(10, 0, -10, 0, Material.AIR))
        assertFalse(DomainLighting.canFill(10, 0, -11, 0, Material.AIR))
        assertFalse(DomainLighting.canFill(10, 10, 0, 0, Material.AIR))
        assertFalse(DomainLighting.canFill(10, 0, 10, 0, Material.AIR))
        assertFalse(DomainLighting.canFill(10, -8, 0, -8, Material.AIR))
    }

    @Test fun `light replacement shares original terrain snapshot and restores old light level`() {
        for (original in listOf("air", "stone", "light[level=7]")) {
            var current = original
            val history = DomainBlockLedger<Int, String> { current = it; true }
            history.capture(0) { current }
            current = "air"
            history.capture(0) { current }
            current = "light[level=15]"
            history.restore(0)
            assertEquals(original, current)
            assertTrue(history.keys().isEmpty())
        }
    }
}
