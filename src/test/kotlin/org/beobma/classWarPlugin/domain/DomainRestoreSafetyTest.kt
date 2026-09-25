package org.beobma.classWarPlugin.domain

import org.bukkit.util.BoundingBox
import kotlin.test.*

class DomainRestoreSafetyTest {
    @Test fun `occupied original is retained and restored exactly once after evacuation`() {
        val restored = mutableListOf<String>()
        val ledger = DomainBlockLedger<Int, String> { restored += it; true }
        ledger.capture(1) { "original chest and inventory" }
        assertFalse(ledger.restoreIf(1) { false })
        assertEquals("original chest and inventory", ledger.peek(1))
        assertTrue(restored.isEmpty())
        ledger.capture(1) { "temporary domain block" }
        assertTrue(ledger.restoreIf(1) { true })
        assertNull(ledger.peek(1))
        assertTrue(ledger.restoreIf(1) { true })
        assertEquals(listOf("original chest and inventory"), restored)
    }

    @Test fun `side overlap and scaled player height are detected beyond feet and head cells`() {
        val box = BoundingBox(0.8, 5.0, 0.2, 1.4, 8.5, 0.8)
        assertTrue(DomainRestoreSafety.overlaps(box, 1, 5, 0))
        assertTrue(DomainRestoreSafety.overlaps(box, 1, 8, 0))
        assertFalse(DomainRestoreSafety.overlaps(box, 1, 4, 0))
        assertFalse(DomainRestoreSafety.overlaps(box, 2, 6, 0))
    }
}
