package org.beobma.classWarPlugin.gameClass.relic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpearThrowCooldownTest {
    @Test fun `throw stays locked for forty ticks even while spear is already recovered`() {
        val cooldown = SpearThrowCooldown()
        assertTrue(cooldown.ready)
        repeat(3) {
            cooldown.onThrown()
            repeat(40) {
                assertFalse(cooldown.ready)
                cooldown.tick()
            }
            assertTrue(cooldown.ready)
            repeat(10) { cooldown.tick() }
            assertTrue(cooldown.ready)
        }
    }
}
