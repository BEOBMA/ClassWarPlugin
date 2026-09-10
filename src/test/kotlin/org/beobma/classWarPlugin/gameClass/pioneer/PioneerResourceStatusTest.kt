package org.beobma.classWarPlugin.gameClass.pioneer

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.list.*
import java.util.UUID
import kotlin.test.*

class PioneerResourceStatusTest {
    @Test fun `all resources use searchable keywords and remain visible at zero`() {
        val resources = listOf(
            ForesightStatus() to Keyword.Foresight,
            AccelerationStatus() to Keyword.Acceleration,
            AccelerationBulletStatus() to Keyword.AccelerationBullet,
            DisposalStatus() to Keyword.Disposal,
        )
        for ((status, keyword) in resources) {
            assertEquals(keyword.string, status.name)
            assertEquals(listOf(keyword.requireDescription()), status.description)
            assertTrue(status.isClassMechanic)
            assertTrue(status.showInActionBar)
            assertFalse(status.canRemove)
            assertTrue(status.synchronize(0))
            assertFalse(status.synchronize(0))
            assertTrue(status.actionBarText().contains("<dark_gray>0</dark_gray>"))
            assertNull(status.duration)
        }
        assertSame(Keyword.Foresight, Keyword.find("예지안"))
        assertSame(Keyword.Acceleration, Keyword.find("가속"))
        assertSame(Keyword.AccelerationBullet, Keyword.find("가속탄"))
        assertSame(Keyword.Disposal, Keyword.find("처분"))
    }

    @Test fun `bullet and chain changes refresh independently of foresight`() {
        val foresight = ForesightStatus()
        val bullets = AccelerationBulletStatus()
        val chain = DisposalStatus()
        foresight.synchronize(30)
        bullets.synchronize(0)
        chain.synchronize(0)
        assertFalse(foresight.synchronize(30))
        assertTrue(bullets.synchronize(2))
        assertTrue(chain.synchronize(1, 200))
        assertTrue(chain.actionBarText().contains("10.0초"))
        assertFalse(chain.synchronize(1, 199))
        assertTrue(chain.synchronize(1, 198))
        assertTrue(chain.actionBarText().contains("9.9초"))
        assertTrue(chain.synchronize(0, 0))
        assertFalse(chain.actionBarText().contains("초"))
    }

    @Test fun `remaining time follows actual hit and chain deadlines and is read only`() {
        val state = PioneerState()
        val target = UUID.randomUUID()
        state.hit(target, 100)
        assertEquals(80, state.accelerationRemainingTicks(100))
        assertEquals(1, state.accelerationRemainingTicks(179))
        assertEquals(0, state.accelerationRemainingTicks(180))
        assertEquals(1, state.acceleration)
        state.hit(target, 160)
        assertEquals(80, state.accelerationRemainingTicks(160))
        state.advanceChain(200)
        assertEquals(200, state.chainRemainingTicks(200))
        state.advanceChain(260)
        assertEquals(140, state.chainRemainingTicks(260))
        state.endChain()
        assertEquals(0, state.chainRemainingTicks(260))
    }
}
