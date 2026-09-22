package org.beobma.classWarPlugin.domain

import org.beobma.classWarPlugin.gameClass.referee.CrimeLedger
import org.beobma.classWarPlugin.gameClass.referee.CrimeRecord
import org.beobma.classWarPlugin.gameClass.referee.CrimeType
import org.beobma.classWarPlugin.gameClass.referee.Plea
import java.util.UUID
import kotlin.math.abs
import kotlin.test.*

class DomainPresentationTest {
    @Test fun `training falls back to caster but live targeted domain requires an opponent`() {
        assertNull(DomainTarget.LOOKING_AT_PLAYER.resolve(null, "caster", false))
        assertEquals("caster", DomainTarget.LOOKING_AT_PLAYER.resolve(null, "caster", true))
        for (training in listOf(false, true)) {
            assertEquals("opponent", DomainTarget.LOOKING_AT_PLAYER.resolve("opponent", "caster", training))
            assertNull(DomainTarget.SURROUNDING.resolve("opponent", "caster", training))
        }
    }

    @Test fun `training charge permits solo trial without fabricating real evidence`() {
        val id = UUID.randomUUID()
        val ledger = CrimeLedger()
        assertNull(CrimeLedger.trialCharge(ledger.heaviest(id), false, id, 40))
        val preview = assertNotNull(CrimeLedger.trialCharge(ledger.heaviest(id), true, id, 40))
        assertEquals(id, preview.offender)
        assertEquals(CrimeType.ASSAULT, preview.type)
        assertEquals(0, CrimeLedger.verdict(preview, Plea.SELF_DEFENSE, 0).severity)
        assertNull(ledger.heaviest(id))
        assertEquals(0, ledger.murders(id))
    }

    @Test fun `training does not overwrite existing charges`() {
        val id = UUID.randomUUID()
        val charge = CrimeRecord(CrimeType.MURDER, id, UUID.randomUUID(), "victim", 3, 0.0, false)
        for (training in listOf(false, true)) assertSame(charge, CrimeLedger.trialCharge(charge, training, id, 99))
    }

    @Test fun `dome geometry is finite contained and bounded at every supported radius`() {
        for (r in 4..24) {
            val radius = r - 1.8
            for (progress in listOf(-1.0, 0.0, 0.25, 0.75, 1.0, 2.0)) {
                val crown = DomainEffectGeometry.crown(radius, progress, 0.5)
                val arcs = DomainEffectGeometry.meridians(radius, 0.4)
                assertEquals(64, crown.size)
                assertEquals(72, arcs.size)
                (crown + arcs).forEach {
                    assertTrue(it.x.isFinite() && it.y.isFinite() && it.z.isFinite())
                    assertTrue(it.y in 0.0..radius)
                    assertTrue(abs(it.x * it.x + it.y * it.y + it.z * it.z - radius * radius) < 1e-7)
                }
            }
        }
    }

    @Test fun `rings rotate and collapse without growing particle counts`() {
        val first = DomainEffectGeometry.ring(10.0, 0.2, 0.0)
        val next = DomainEffectGeometry.ring(10.0, 0.2, 0.1)
        assertNotEquals(first, next)
        assertEquals(first.size, next.size)
        assertTrue(DomainEffectGeometry.ring(0.0, 0.2).all { it.x == 0.0 && it.z == 0.0 })
    }
}
