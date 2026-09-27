package org.beobma.classWarPlugin.description

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.ItemDescriptionManager
import kotlin.test.*

class KeywordLoreLayoutTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test fun `keyword heading is separate and every body line is indented`() {
        for (keyword in Keyword.describedEntries) {
            val lines = ItemDescriptionManager.renderKeywordExplanation(keyword.requireDescription()).map(plain::serialize)
            assertTrue(lines.size >= 2, keyword.name)
            assertTrue(lines.first().startsWith("◆ "), keyword.name)
            assertTrue(lines.drop(1).all { it.startsWith("  ") }, keyword.name)
            assertFalse(lines.joinToString("").contains("{keyword:"), keyword.name)
        }
    }

    @Test fun `ordinary description remains a single unchanged line`() {
        val text = "일반 설명은 원래 작성한 줄을 그대로 유지한다. ".repeat(10)
        assertEquals(text, plain.serialize(ItemDescriptionManager.renderLoreLine("<gray>$text")))
    }
}
