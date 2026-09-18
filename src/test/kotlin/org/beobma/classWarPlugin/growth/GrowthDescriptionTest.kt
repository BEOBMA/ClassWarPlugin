package org.beobma.classWarPlugin.growth

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.description.DescriptionText
import org.beobma.classWarPlugin.description.GrowthDescription
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.ItemDescriptionManager
import org.beobma.classWarPlugin.manager.UtilManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class GrowthDescriptionTest {
    private fun context(id: String = "swordplay", points: Int = 0) =
        GrowthDescription.Context(id, GrowthProfile.forClass(id), { points })

    // These constructors allocate Paper ItemStacks, which need a live server registry.
    // Validate their literal bindings too, without pretending to have booted Paper here.
    private val serverConstructors = mapOf("solar-system" to "SolarSystem", "uranus" to "Uranus", "sagittarius" to "Sagittarius")
    private fun descriptions(id: String): List<List<String>> {
        serverConstructors[id]?.let { name ->
            return listOf(Files.readAllLines(Path.of("src/main/kotlin/org/beobma/classWarPlugin/gameClass/list/$name.kt"))
                .filter { it.contains('"') && (it.contains("{g:") || it.contains("{keyword:")) })
        }
        val ability = AbilityCatalog.create(id)
        return listOf(ability.weapon.description, ability.weapon.briefDescription) +
            ability.skills.flatMap { listOf(it.description, it.briefDescription, ItemDescriptionManager.cooldownLines(it.cooldown)) } +
            ability.passives.flatMap { listOf(it.description, it.briefDescription) }
    }

    @Test fun `example uses final damage and additive stat coefficients inline`() {
        val profile = GrowthProfile(GrowthStat.STRENGTH, GrowthStat.AGILITY, primaryPercent = 1.0, secondaryPercent = 0.0)
        val ctx = GrowthDescription.Context("example", profile, { if (it == GrowthStat.STRENGTH) 100 else 0 })
        assertEquals("<red><green>20</green>(힘 10%)의 피해를 입힌다.",
            GrowthDescription.render("<red>{g:damage:10}의 피해를 입힌다.", ctx))
    }

    @Test fun `zero growth keeps original color and restores following text color`() {
        fun leaves(c: Component, parent: TextColor? = null): List<Pair<String, TextColor?>> {
            val color = c.color() ?: parent
            return listOfNotNull((c as? TextComponent)?.content()?.takeIf { it.isNotEmpty() }?.let { it to color }) +
                c.children().flatMap { leaves(it, color) }
        }
        val raw = "<red>{g:damage:10}의 피해</red><aqua>를 입힌다"
        val unchanged = GrowthDescription.render(raw, context())
        assertFalse(unchanged.contains("<green>"))
        val changed = GrowthDescription.render(raw, context(points = 100))
        val content = leaves(MiniMessage.miniMessage().deserialize(changed))
        assertTrue(content.any { it.second == NamedTextColor.GREEN && it.first.toDoubleOrNull() != null })
        assertTrue(content.any { it.second == NamedTextColor.RED && it.first.contains("피해") })
        assertTrue(content.any { it.second == NamedTextColor.AQUA && it.first.contains("입힌다") })
    }

    @Test fun `classic descriptions expose only original numbers without internal tokens`() {
        val input = "<gold>{g:damage:10}의 피해, {g:feature/passive-swords:3}자루, {g:cooldown:20}초"
        assertEquals("<gold>10의 피해, 3자루, 20초", GrowthDescription.render(input, null))
        assertEquals("<gold>10의 피해, 3자루, 20초", GrowthDescription.baseText(input))
        assertFalse(UtilManager.applyKeywords(input).contains("{g:"))
    }

    @Test fun `cooldown reduction is intelligence only and not colored green`() {
        val profile = GrowthProfile(GrowthStat.AGILITY, GrowthStat.STRENGTH)
        val raw = "<gray>{g:cooldown:20}초"
        val agility = GrowthDescription.Context("example", profile, { if (it == GrowthStat.AGILITY) 100 else 0 })
        val intelligence = agility.copy(stats = { if (it == GrowthStat.INTELLIGENCE) 100 else 0 })
        assertContains(GrowthDescription.render(raw, agility), "20(")
        val reduced = GrowthDescription.render(raw, intelligence)
        assertContains(reduced, "13.35(")
        assertContains(reduced, "지능 0.5% 회복 속도")
        assertFalse(reduced.contains("<green>"))
    }

    @Test fun `summon numbers follow discrete thresholds and capacity ceiling`() {
        val raw = "{g:feature/passive-swords:3}자루 / {g:feature/infinite-swords:18}자루"
        assertContains(GrowthDescription.render(raw, context(points = 19)), "3(민첩 20당 +1")
        assertContains(GrowthDescription.render(raw, context(points = 20)), "<green>4</green>")
        val capped = GrowthDescription.render(raw, context(points = 10000))
        assertContains(capped, "<green>9</green>")
        assertContains(capped, "<green>36</green>")
    }

    @Test fun `rounding and raw timers mirror the respective execution paths`() {
        val ctx = context(points = 10)
        assertContains(GrowthDescription.render("{g:duration:8}", ctx), "8(")
        assertContains(GrowthDescription.render("{g:time:8}", ctx), "8.3")
        assertContains(GrowthDescription.render("{g:reload-seconds:20}", ctx), "20(")
        assertContains(GrowthDescription.render("{g:feature/code-time:35}", context("hacker", 10)), "36")
    }

    @Test fun `configured base factors and positive integer floors are reflected in previews`() {
        val ctx = context().copy(baseMultipliers = mapOf("damage" to 2.0, "speed" to 0.1, "duration" to 0.1))
        assertEquals(20.0, GrowthDescription.evaluate("damage", 10.0, ctx).first)
        assertEquals(1.0, GrowthDescription.evaluate("speed", 1.0, ctx).first)
        assertEquals(1.0, GrowthDescription.evaluate("duration", 1.0, ctx).first)
        assertEquals(0.0, GrowthDescription.evaluate("duration", 0.0, ctx).first)
        assertEquals(10.0, GrowthDescription.evaluate("cooldown", 20.0, ctx.copy(cooldownFlow = 2.0)).first)
        // Direct health grants are neither status-power scaled nor rounded to an integer.
        val health = context(points = 3).copy(baseMultipliers = mapOf("shield" to 2.0))
        assertEquals(10.24, GrowthDescription.evaluate("health", 10.0, health).first, 0.000001)
    }

    @Test fun `all class descriptions and keyword explanations render in detailed and brief modes`() {
        var bindings = 0
        for (id in GrowthClassCatalog.styles.keys) {
            for (lines in descriptions(id)) {
                val withKeywords = lines + Keyword.explanationsFor(lines)
                for (points in listOf(0, 100, 10000)) {
                    val output = GrowthDescription.render(withKeywords, context(id, points))
                    output.forEach { line ->
                        assertFalse(line.contains("{g:"), "$id has an unresolved binding: $line")
                        assertFalse(line.contains("NaN") || line.contains("Infinity"), id)
                        MiniMessage.miniMessage().deserialize(UtilManager.applyKeywords(line))
                    }
                }
                bindings += withKeywords.count { it.contains("{g:") }
                val classic = GrowthDescription.render(withKeywords, null)
                assertTrue(classic.none { it.contains("{g:") }, id)
            }
        }
        assertTrue(bindings > 250, "Expected class-wide numeric bindings, found $bindings")
    }

    @Test fun `each bespoke growth feature is represented in its actual class descriptions`() {
        for ((id, rules) in GrowthClassCatalog.features) {
            val descriptions = descriptions(id).flatten().joinToString("\n")
            for (key in rules.keys) {
                val token = if (id == "writer") "{g:writer-reward:" else "{g:feature/$key:"
                assertContains(descriptions, token, message = "$id/$key")
            }
        }
    }

    @Test fun `game rule numbers remain unchanged next to growing damage`() {
        val swordplay = AbilityCatalog.create("swordplay")
        val passive = GrowthDescription.render(swordplay.passives.first().description, context(points = 100))
        assertTrue(passive.any { it.contains("3번 피격될 때마다") })
        assertTrue(passive.any { it.contains("3초가 지나면") })
        assertTrue(passive.any { it.contains("<green>8</green>") })
        val knight = AbilityCatalog.create("knight")
        assertTrue(GrowthDescription.render(knight.weapon.description, context("knight", 100)).any { it.contains("24초마다") })
    }

    @Test fun `equipment changes recalculate from originals without accumulating old values`() {
        val state = GrowthPlayerState()
        val ctx = context().copy(stats = state::stat)
        val raw = "{g:damage:10}"
        val original = GrowthDescription.render(raw, ctx)
        state.inventory += "world-tree"
        assertTrue(state.equip("world-tree"))
        val equipped = GrowthDescription.render(raw, ctx)
        assertNotEquals(original, equipped)
        assertEquals(equipped, GrowthDescription.render(raw, ctx))
        state.equip("world-tree")
        assertEquals(original, GrowthDescription.render(raw, ctx))
        assertEquals("10", DescriptionText.plain(GrowthDescription.baseText(raw)))
    }
}
