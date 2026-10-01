package org.beobma.classWarPlugin.damage

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.util.DamageType
import kotlin.test.*

class DamageAppearanceTest {
    @Test fun `resonance consumption shows its name without a zero damage amount`() {
        val parser = MiniMessage.builder().strict(true).build()
        assertEquals("공명", text(parser.deserialize(DamageAppearance.RESONANCE.markup(0.0, labelOnly = true))))
        assertEquals("공명", text(parser.deserialize(DamageAppearance.RESONANCE.markup(0.0, outline = true, labelOnly = true))))
        assertEquals("결산\n-17", text(parser.deserialize(DamageAppearance.SETTLEMENT.markup(17.0))))
    }
    private fun text(c: Component): String = ((c as? TextComponent)?.content() ?: "") + c.children().joinToString("") { text(it) }
    @Test fun `special labels and strokes render identically without formatting tokens leaking`() {
        val parser = MiniMessage.builder().strict(true).build()
        for (style in DamageAppearance.entries) {
            val front = text(parser.deserialize(style.markup(127.25)))
            val stroke = text(parser.deserialize(style.markup(127.25, true)))
            assertEquals(front, stroke)
            assertFalse(front.contains('<'))
            assertFalse(front.contains("!!"))
            if (style != DamageAppearance.EXECUTION) assertTrue(front.contains("-127.25"))
            else { assertEquals("처형", front); assertFalse(front.contains("127")) }
        }
    }
    @Test fun `normal hits retain the original compact number while special hits add their identity`() {
        val parser = MiniMessage.miniMessage()
        assertEquals("-2", text(parser.deserialize(DamageAppearance.NORMAL.markup(2.0))))
        assertEquals("진동 폭발\n-0.2", text(parser.deserialize(DamageAppearance.VIBRATION.markup(0.2))))
        assertContains(DamageAppearance.FIXED.markup(2.0), "minecraft:uniform")
    }
    @Test fun `fallback mapping distinguishes fixed status and ordinary skill damage`() {
        assertEquals(DamageAppearance.FIXED, DamageAppearance.resolve(DamageType.True, DamagePath.SKILL))
        assertEquals(DamageAppearance.STATUS, DamageAppearance.resolve(DamageType.StatusAbnormality))
        assertEquals(DamageAppearance.STATUS, DamageAppearance.resolve(DamageType.Normal, DamagePath.STATUS_EFFECT))
        assertEquals(DamageAppearance.NORMAL, DamageAppearance.resolve(DamageType.Normal, DamagePath.SKILL))
    }
    @Test fun `pop animation settles to base scale and all lifetimes are bounded`() {
        DamageAppearance.entries.forEach { style ->
            assertTrue(style.lifetime in 30..44)
            for (age in -1..60) assertTrue(style.animatedScale(age).isFinite() && style.animatedScale(age) in 1f..2.3f)
            assertEquals(style.scale, style.animatedScale(6))
            assertEquals(style.scale, style.animatedScale(50))
        }
        assertEquals(1f, DamageAppearance.NORMAL.animatedScale(0))
        assertTrue(DamageAppearance.EXECUTION.animatedScale(0) > DamageAppearance.EXECUTION.scale)
    }
    @Test fun `vibration shake alternates and stops instead of drifting`() {
        assertTrue(DamageAppearance.VIBRATION.shake(1) < 0)
        assertTrue(DamageAppearance.VIBRATION.shake(2) > 0)
        assertEquals(0.0, DamageAppearance.VIBRATION.shake(11))
        DamageAppearance.entries.filter { it != DamageAppearance.VIBRATION }.forEach { assertEquals(0.0, it.shake(2)) }
    }
}
