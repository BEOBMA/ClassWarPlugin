package org.beobma.classWarPlugin.keyword

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import kotlin.test.*

class StatusIconTest {
    private fun render(component: Component, translations: Map<String, String>): String =
        (when (component) {
            is TranslatableComponent -> translations[component.key()] ?: component.fallback() ?: component.key()
            is TextComponent -> component.content()
            else -> ""
        }) + component.children().joinToString("") { render(it, translations) }

    @Test fun `missing pack produces only original text with no replacement symbol or leading space`() {
        for (keyword in Keyword.entries) {
            val component = MiniMessage.miniMessage().deserialize(keyword.string)
            assertEquals(keyword.displayName, render(component, emptyMap()))
        }
    }

    @Test fun `installed translation prepends icon and spacing only once`() {
        val component = MiniMessage.miniMessage().deserialize(Keyword.Bleeding.string)
        assertEquals("\uE000 출혈", render(component, mapOf("classwar.icon.bleeding" to "\uE000 ")))
        assertEquals("출혈", Keyword.Bleeding.displayName)
        assertSame(Keyword.Bleeding, Keyword.find("출혈"))
    }

    @Test fun `icon markup is scoped valid minimessage and rejects injection`() {
        MiniMessage.builder().strict(true).build().deserialize(StatusIcon.markup("Burn"))
        assertFailsWith<IllegalArgumentException> { StatusIcon.markup("Burn:'bad'") }
    }
}
