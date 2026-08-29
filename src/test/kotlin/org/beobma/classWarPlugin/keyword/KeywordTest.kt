package org.beobma.classWarPlugin.keyword

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeywordTest {
    @Test
    fun `status keywords always provide descriptions`() {
        val statusKeywords = listOf(
            Keyword.Abyss,
            Keyword.Electrocution,
            Keyword.Freezing,
            Keyword.Frostbite,
            Keyword.Shield,
            Keyword.Silence,
            Keyword.Stealth,
            Keyword.Stun,
            Keyword.Vibration,
            Keyword.VibrationExplosion,
        )

        statusKeywords.forEach { keyword ->
            assertEquals(keyword.description, keyword.requireDescription())
        }
    }

    @Test
    fun `missing required description fails with a useful error`() {
        assertFailsWith<IllegalArgumentException> {
            Keyword.Arrow.requireDescription()
        }
    }
}
