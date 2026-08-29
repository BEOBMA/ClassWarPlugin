package org.beobma.classWarPlugin.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchModeTest {
    @Test
    fun `tail tag dual assigns two classes and uses tail rules`() {
        assertEquals(2, MatchMode.TAIL_TAG_DUAL.assignedClassCount)
        assertTrue(MatchMode.TAIL_TAG_DUAL.usesTailTagRules)
    }

    @Test
    fun `parasite is excluded from both tail modes`() {
        assertFalse(MatchMode.TAIL_TAG.allowsParasite)
        assertFalse(MatchMode.TAIL_TAG_DUAL.allowsParasite)
        assertTrue(MatchMode.CLASSIC.allowsParasite)
        assertTrue(MatchMode.DUAL.allowsParasite)
    }
}
