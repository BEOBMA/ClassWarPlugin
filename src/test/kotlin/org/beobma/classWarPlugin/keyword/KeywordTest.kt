package org.beobma.classWarPlugin.keyword

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KeywordTest {
    @Test
    fun `photography stack is bold and available in the keyword dictionary and brief description`() {
        assertSame(Keyword.PhotographyStack, Keyword.find("촬영 스택"))
        kotlin.test.assertTrue(Keyword.PhotographyStack.string.contains("<bold>촬영 스택</bold>"))
        assertEquals(listOf(Keyword.PhotographyStack.requireDescription()),
            Keyword.briefExplanationsFor(listOf("{keyword:PhotographyStack}을 1 얻는다.")))
    }
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

    @Test
    fun `described keyword can be found by its plain korean name`() {
        assertSame(Keyword.VibrationExplosion, Keyword.find("진동 폭발"))
        assertSame(Keyword.VibrationExplosion, Keyword.find("  진동 폭발  "))
        assertEquals(null, Keyword.find("vibrationexplosion"))
        assertEquals(null, Keyword.find("<gold>진동 폭발"))
    }

    @Test
    fun `keywords without descriptions are excluded from the dictionary`() {
        assertEquals(null, Keyword.find("화살"))
        assertEquals(null, Keyword.find("Arrow"))
    }
}
