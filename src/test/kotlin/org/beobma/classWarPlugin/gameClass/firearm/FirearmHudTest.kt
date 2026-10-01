package org.beobma.classWarPlugin.gameClass.firearm

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.*

class FirearmHudTest {
    private val miniMessage = MiniMessage.builder().strict(true).build()
    private fun plain(frame: String) = PlainTextComponentSerializer.plainText()
        .serialize(miniMessage.deserialize(frame))

    @Test fun `every successful shot immediately has a distinct ammunition frame`() {
        for (profile in FirearmProfile.entries) {
            val magazine = FirearmMagazine(profile)
            val frames = mutableSetOf(FirearmHud.render(magazine))
            repeat(profile.capacity / profile.cost) { shot ->
                assertTrue(magazine.shoot(shot.toLong() * profile.interval))
                assertTrue(frames.add(FirearmHud.render(magazine)), "$profile shot $shot must change the display")
            }
            assertEquals(profile.capacity / profile.cost + 1, frames.size)
            val empty = FirearmHud.render(magazine)
            assertFalse(magazine.shoot(100000))
            assertEquals(empty, FirearmHud.render(magazine))
        }
    }

    @Test fun `all frames are valid markup without names numeric counts or countdowns`() {
        for (profile in FirearmProfile.entries) {
            val magazine = FirearmMagazine(profile)
            val frames = mutableListOf(FirearmHud.render(magazine))
            magazine.reload()
            frames += FirearmHud.render(magazine)
            repeat(profile.reloadTicks) {
                magazine.tick()
                frames += FirearmHud.render(magazine)
            }
            frames.forEach { frame ->
                val text = plain(frame)
                assertTrue(text.isNotBlank())
                assertFalse(text.any { it.isLetterOrDigit() }, text)
                assertTrue(text.length <= 65, "Compact single-line HUD: $text")
                assertFalse('\n' in text)
            }
        }
    }

    @Test fun `gun silhouettes are distinct without textual labels`() {
        assertEquals(4, FirearmProfile.entries.map { plain(FirearmHud.render(FirearmMagazine(it))) }.toSet().size)
    }

    @Test fun `minigun folds feed into the belt without losing per-round precision`() {
        val magazine = FirearmMagazine(FirearmProfile.MINIGUN)
        repeat(30) { magazine.shoot(it.toLong()) }
        val frame = FirearmHud.render(magazine)
        assertTrue("<gold>${"▣".repeat(8)}</gold>" in frame)
        assertTrue("<yellow>${"╿".repeat(30)}</yellow>" in frame)
        magazine.shoot(30)
        assertTrue("<yellow>${"╿".repeat(29)}</yellow>" in FirearmHud.render(magazine))
    }

    @Test fun `reload has physical stages and fills the real magazine only on completion`() {
        for (profile in FirearmProfile.entries) {
            val magazine = FirearmMagazine(profile)
            val full = FirearmHud.render(magazine)
            magazine.reload()
            val stages = mutableSetOf<String>()
            repeat(profile.reloadTicks) {
                val frame = FirearmHud.render(magazine)
                stages += frame
                assertNotEquals(full, frame)
                assertEquals(0, magazine.bullets)
                assertFalse(magazine.shoot(100000))
                magazine.tick()
            }
            assertTrue(stages.size >= 6, "$profile should visibly eject, insert and lock ammunition")
            assertEquals(profile.capacity, magazine.bullets)
            assertEquals(full, FirearmHud.render(magazine))
        }
    }

    @Test fun `reload animation tracks adjusted combat time and stays still without ticks`() {
        for (profile in FirearmProfile.entries) {
            val normal = FirearmMagazine(profile)
            val shortened = FirearmMagazine(profile)
            normal.reload(100)
            shortened.reload(50)
            repeat(50) {
                assertEquals(FirearmHud.render(normal), FirearmHud.render(shortened))
                val paused = FirearmHud.render(normal)
                repeat(3) { assertEquals(paused, FirearmHud.render(normal)) }
                repeat(2) { normal.tick() }
                shortened.tick()
            }
            assertEquals(FirearmHud.render(normal), FirearmHud.render(shortened))
            shortened.reload(1)
            plain(FirearmHud.render(shortened))
            assertTrue(shortened.tick())
            assertEquals(FirearmHud.render(normal), FirearmHud.render(shortened))
        }
    }
}
