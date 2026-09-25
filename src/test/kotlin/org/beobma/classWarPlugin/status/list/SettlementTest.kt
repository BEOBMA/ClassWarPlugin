package org.beobma.classWarPlugin.status.list

import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementTest {
    @Test fun `damage sums removed power only`() {
        assertEquals(10.0, listOf(2, 3, 5).sumOf { Settlement.contribution(it) })
        assertEquals(0.0, Settlement.contribution(-1))
    }

    @Test fun `burn duration does not change damage or double count the status`() {
        for (ticks in listOf(1, 20, 200, 2000)) {
            assertEquals(1.0, Settlement.burnContribution(ticks, 0))
            assertEquals(1.0, Settlement.burnContribution(ticks, 1))
            assertEquals(4.0, Settlement.burnContribution(ticks, 4))
        }
        assertEquals(0.0, Settlement.burnContribution(0, 0))
        assertEquals(1.0, Settlement.burnContribution(0, 1))
    }
}
