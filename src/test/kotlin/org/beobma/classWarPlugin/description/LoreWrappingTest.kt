package org.beobma.classWarPlugin.description

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.*

class LoreWrappingTest {
    private val plain = PlainTextComponentSerializer.plainText()
    @Test fun `long Korean explanations wrap without losing letters`() {
        val text = "출혈 피해를 입으며 지속 시간이 갱신된다. ".repeat(8).trim()
        val lines = LoreWrapping.wrap(MiniMessage.miniMessage().deserialize("<red><bold>$text"))
        assertTrue(lines.size > 1)
        assertEquals(text.replace(" ", ""), lines.joinToString("") { plain.serialize(it) }.replace(" ", ""))
        assertTrue(lines.all { plain.serialize(it).length <= 40 })
        lines.forEach { line ->
            assertTrue(line.children().all { it.color() == net.kyori.adventure.text.format.NamedTextColor.RED })
        }
    }
    @Test fun `unbroken text and explicit newlines are supported`() {
        assertEquals(3, LoreWrapping.wrap(Component.text("가".repeat(90))).size)
        assertEquals(listOf("짧은 설명", "다음 줄"), LoreWrapping.wrap(Component.text("짧은 설명\n다음 줄")).map(plain::serialize))
        assertEquals("", plain.serialize(LoreWrapping.wrap(Component.empty()).single()))
    }
}
