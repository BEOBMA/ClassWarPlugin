package org.beobma.classWarPlugin.gameClass.referee

import java.util.UUID
import kotlin.test.*

class CrimeLedgerTest {
    private val attacker = UUID.randomUUID()
    private val victim = UUID.randomUUID()

    @Test fun `assault requires accumulated real damage`() {
        val ledger = CrimeLedger()
        ledger.damage(attacker, victim, "victim", 0, 4.0)
        assertNull(ledger.heaviest(attacker))
        ledger.damage(attacker, victim, "victim", 20, 4.0)
        val record = assertNotNull(ledger.heaviest(attacker))
        assertEquals(CrimeType.ASSAULT, record.type)
        assertEquals(8.0, record.damage)
        assertFalse(record.selfDefense)
    }

    @Test fun `retaliation retains first attacker evidence through murder`() {
        val ledger = CrimeLedger()
        ledger.damage(attacker, victim, "victim", 0, 1.0)
        ledger.damage(victim, attacker, "attacker", 1, 10.0)
        ledger.murder(victim, attacker, "attacker", 2)
        val record = assertNotNull(ledger.heaviest(victim))
        assertEquals(CrimeType.MURDER, record.type)
        assertTrue(record.selfDefense)
        assertEquals(Verdict(0), CrimeLedger.verdict(record, Plea.SELF_DEFENSE, 1))
    }

    @Test fun `ceasefire resets aggression and partial damage`() {
        val ledger = CrimeLedger()
        ledger.damage(attacker, victim, "victim", 0, 4.0)
        ledger.damage(attacker, victim, "victim", 201, 4.0)
        assertNull(ledger.heaviest(attacker))
        ledger.damage(victim, attacker, "attacker", 402, 8.0)
        assertFalse(assertNotNull(ledger.heaviest(victim)).selfDefense)
    }

    @Test fun `execution needs repeated murder and a false plea`() {
        val murder = CrimeRecord(CrimeType.MURDER, attacker, victim, "victim", 0, 0.0, false)
        assertEquals(Verdict(2), CrimeLedger.verdict(murder, Plea.CONFESS, 5))
        assertEquals(Verdict(3), CrimeLedger.verdict(murder, null, 5))
        assertEquals(Verdict(3, true), CrimeLedger.verdict(murder, Plea.DENY, 1))
        assertEquals(Verdict(4, true), CrimeLedger.verdict(murder, Plea.DENY, 2))
        assertEquals(Verdict(4, true), CrimeLedger.verdict(murder, Plea.SELF_DEFENSE, 2))
        assertEquals(Verdict(0), CrimeLedger.verdict(murder.copy(selfDefense = true), Plea.SELF_DEFENSE, 5))
        assertEquals(Verdict(3, true), CrimeLedger.verdict(murder.copy(type = CrimeType.ASSAULT), Plea.DENY, 5))
    }

    @Test fun `a continuous encounter does not accumulate old chip damage indefinitely`() {
        val ledger = CrimeLedger()
        for (tick in 0L..1000L step 100) ledger.damage(attacker, victim, "victim", tick, 1.0)
        assertNull(ledger.heaviest(attacker))
    }

    @Test fun `invalid damage and self kills do not create evidence`() {
        val ledger = CrimeLedger()
        listOf(0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            ledger.damage(attacker, victim, "victim", 0, it)
        }
        ledger.murder(attacker, attacker, "self", 0)
        assertNull(ledger.heaviest(attacker))
    }

    @Test fun `records are owned by each referee and cleared after trial`() {
        val first = CrimeLedger()
        val second = CrimeLedger()
        first.murder(attacker, victim, "victim", 0)
        second.murder(attacker, victim, "victim", 0)
        first.clear()
        assertNull(first.heaviest(attacker))
        assertEquals(0, first.murders(attacker))
        assertEquals(1, second.murders(attacker))
    }
}
