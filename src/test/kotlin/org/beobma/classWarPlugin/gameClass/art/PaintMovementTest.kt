package org.beobma.classWarPlugin.gameClass.art

import org.bukkit.util.Vector
import kotlin.test.*

class PaintMovementTest {
    @Test fun `WASD follows fixed camera axes`() {
        val speed = PaintMovement.SPEED
        assertEquals(Vector(0.0, 0.0, speed), PaintMovement.step(true, false, false, false))
        assertEquals(Vector(0.0, 0.0, -speed), PaintMovement.step(false, true, false, false))
        assertEquals(Vector(speed, 0.0, 0.0), PaintMovement.step(false, false, true, false))
        assertEquals(Vector(-speed, 0.0, 0.0), PaintMovement.step(false, false, false, true))
    }

    @Test fun `released and opposing keys cause no movement`() {
        assertEquals(Vector(), PaintMovement.step(false, false, false, false))
        assertEquals(Vector(), PaintMovement.step(true, true, false, false))
        assertEquals(Vector(), PaintMovement.step(false, false, true, true))
        assertEquals(Vector(), PaintMovement.step(true, true, true, true))
    }

    @Test fun `every key combination stays horizontal with equal diagonal speed`() {
        for (keys in 0..15) {
            val delta = PaintMovement.step(keys and 1 != 0, keys and 2 != 0, keys and 4 != 0, keys and 8 != 0)
            assertEquals(0.0, delta.y)
            if (delta.lengthSquared() > 0.0) assertEquals(PaintMovement.SPEED, delta.length(), 1e-9)
        }
    }
}
