package org.beobma.classWarPlugin.gameClass.mechanics

import org.bukkit.util.Vector
import kotlin.test.*

class ClassMechanicsTest {
    @Test fun `swords require a hit and stop exactly after three seconds`() {
        val window = RecentBasicAttack()
        assertFalse(window.isActive(0))
        window.record(100)
        assertTrue(window.isActive(100))
        assertTrue(window.isActive(159))
        assertFalse(window.isActive(160))
        window.record(155)
        assertTrue(window.isActive(160))
        assertFalse(window.isActive(215))
        window.reset()
        assertFalse(window.isActive(155))
    }

    @Test fun `black hole retains outward momentum and jumping velocity`() {
        val velocity = Vector(0.28, 0.42, 0.0)
        val result = BlackHolePull.apply(velocity, Vector(-5.0, -2.0, 0.0))
        assertEquals(0.245, result.x, 1e-10)
        assertEquals(0.42, result.y)
        assertTrue(result.x > 0.0)
        assertEquals(0.28, velocity.x)
    }

    @Test fun `black hole center has no undefined direction or vertical pull`() {
        val velocity = Vector(0.0, -0.5, 0.0)
        assertEquals(velocity, BlackHolePull.apply(velocity, Vector(0.0, 2.0, 0.0)))
        assertEquals(0.0035, BlackHolePull.apply(Vector(), Vector(0.1, 3.0, 0.0)).x, 1e-10)
    }

    @Test fun `revolver timeline ejects inserts six rounds then locks`() {
        assertEquals("탄피 배출", RevolverReloadFrame.at(40).label)
        assertEquals(0, RevolverReloadFrame.at(32).loadedChambers)
        (1..6).forEach { loaded ->
            assertEquals(loaded, RevolverReloadFrame.at(32 - loaded * 4).loadedChambers)
        }
        assertEquals("실린더 잠금", RevolverReloadFrame.at(6).label)
        assertEquals("장전 완료", RevolverReloadFrame.at(0).label)
        assertEquals(0, RevolverReloadFrame.at(100).loadedChambers)
        assertEquals(6, RevolverReloadFrame.at(-1).loadedChambers)
    }
}
