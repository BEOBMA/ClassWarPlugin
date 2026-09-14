package org.beobma.classWarPlugin.manager

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpawnSurfacePolicyTest {
    @Test
    fun `normal rolling terrain is accepted`() {
        assertFalse(SpawnSurfacePolicy.isDeepDepression(70, listOf(68, 69, 70, 70, 71, 72, 72, 73), 8))
    }

    @Test
    fun `deep ravine floor is rejected`() {
        assertTrue(SpawnSurfacePolicy.isDeepDepression(45, listOf(68, 69, 70, 70, 71, 72, 73, 74), 8))
    }

    @Test
    fun `single high outlier does not reject a valid surface`() {
        assertFalse(SpawnSurfacePolicy.isDeepDepression(70, listOf(69, 69, 70, 70, 71, 71, 72, 120), 8))
    }

    @Test
    fun `drop at threshold is accepted and one block deeper is rejected`() {
        val surroundings = List(8) { 80 }
        assertFalse(SpawnSurfacePolicy.isDeepDepression(72, surroundings, 8))
        assertTrue(SpawnSurfacePolicy.isDeepDepression(71, surroundings, 8))
    }

    @Test
    fun `empty samples fail open and negative threshold is normalized`() {
        assertFalse(SpawnSurfacePolicy.isDeepDepression(0, emptyList(), 8))
        assertTrue(SpawnSurfacePolicy.isDeepDepression(9, listOf(10), -4))
    }

    @Test
    fun `extreme heights do not overflow median arithmetic`() {
        assertFalse(SpawnSurfacePolicy.isDeepDepression(Int.MAX_VALUE, listOf(Int.MAX_VALUE, Int.MAX_VALUE), 0))
        assertTrue(SpawnSurfacePolicy.isDeepDepression(Int.MIN_VALUE, listOf(Int.MAX_VALUE, Int.MAX_VALUE), 8))
    }
}
