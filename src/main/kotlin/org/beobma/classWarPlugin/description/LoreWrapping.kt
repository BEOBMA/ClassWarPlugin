package org.beobma.classWarPlugin.description

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration

/** Wrap rendered text, never MiniMessage tags; retain inherited styling on every glyph. */
internal object LoreWrapping {
    private data class Glyph(val text: String, val style: Style, val width: Int)

    fun wrap(component: Component, maxWidth: Int = 280): List<Component> {
        require(maxWidth > 0)
        val glyphs = mutableListOf<Glyph>()
        fun visit(node: Component, parent: Style) {
            val style = node.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            if (node is TextComponent) node.content().codePoints().forEach { code ->
                val text = String(Character.toChars(code))
                val width = when {
                    code == 10 -> 0
                    code == 32 -> 4
                    code > 127 -> 9
                    text in listOf("i", "l", ".", ",", "!", ":", ";", "'") -> 3
                    else -> 6
                } + if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) 1 else 0
                glyphs += Glyph(text, style, width)
            }
            node.children().forEach { visit(it, style) }
        }
        visit(component, Style.empty())
        val result = mutableListOf<Component>()
        var pending = mutableListOf<Glyph>()
        fun emit(part: List<Glyph>) {
            val line = Component.text().decoration(TextDecoration.ITALIC, false)
            part.forEach { line.append(Component.text(it.text).style(it.style)) }
            result += line.build()
        }
        for (glyph in glyphs) {
            if (glyph.text == "\n") {
                emit(pending); pending.clear(); continue
            }
            while (pending.isNotEmpty() && pending.sumOf { it.width } + glyph.width > maxWidth) {
                val space = pending.indexOfLast { it.text == " " }
                if (space > 0) {
                    emit(pending.take(space))
                    pending = pending.drop(space + 1).toMutableList()
                } else {
                    emit(pending); pending.clear()
                }
            }
            if (pending.isNotEmpty() || glyph.text != " ") pending += glyph
        }
        if (pending.isNotEmpty() || result.isEmpty() || glyphs.lastOrNull()?.text == "\n") emit(pending)
        return result
    }
}
