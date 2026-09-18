package org.beobma.classWarPlugin.growth

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class GrowthClassScalingTest {
    private fun only(stat: GrowthStat, points: Int): (GrowthStat) -> Int = { if (it == stat) points else 0 }

    @Test fun `weapon only compensation buffs damage without nerfing secondary effects`() {
        GrowthClassCatalog.weaponOnlyWeights.forEach { (id, weight) ->
            assertTrue(id in GrowthClassCatalog.styles, id)
            val style = GrowthClassCatalog.style(id)
            val profile = GrowthProfile.forClass(id)
            assertTrue(weight > style.basicWeight, id)
            assertEquals(style.skillWeight, profile.skillDamageWeight, id)
            assertEquals(1.0, profile.multiplier(GrowthAxis.BASIC_DAMAGE) { 0 }, id)
            assertEquals(1.0 + 1.05 * weight, profile.multiplier(GrowthAxis.BASIC_DAMAGE) { 50 }, 1e-9, id)
            assertEquals(profile, GrowthProfile.read(YamlConfiguration(), profile))
        }
        for (id in listOf("ghost", "grass", "peanuts", "swordplay", "terrorist", "ice-wizard")) {
            assertFalse(id in GrowthClassCatalog.weaponOnlyWeights, id)
            assertEquals(GrowthClassCatalog.style(id).basicWeight, GrowthProfile.forClass(id).basicDamageWeight)
        }
    }

    @Test fun `pacifist grows its actual combat tool with strength and retains knockback cap`() {
        val profile = GrowthProfile.forClass("pacifist")
        assertEquals(GrowthStat.STRENGTH, profile.primary)
        assertEquals(1.4, profile.multiplier(GrowthAxis.KNOCKBACK, only(GrowthStat.STRENGTH, 50)), 1e-9)
        assertEquals(1.0, profile.multiplier(GrowthAxis.KNOCKBACK, only(GrowthStat.INTELLIGENCE, 50)))
        assertEquals(1.5, profile.multiplier(GrowthAxis.KNOCKBACK) { Int.MAX_VALUE })
    }

    @Test fun `agility primary and strength secondary cannot shorten cooldowns`() {
        val p = GrowthProfile(GrowthStat.AGILITY, GrowthStat.STRENGTH)
        assertEquals(1.0, p.multiplier(GrowthAxis.COOLDOWN, only(GrowthStat.AGILITY, 100)))
        assertEquals(1.0, p.multiplier(GrowthAxis.COOLDOWN, only(GrowthStat.STRENGTH, 100)))
        assertEquals(1.25, p.multiplier(GrowthAxis.COOLDOWN, only(GrowthStat.INTELLIGENCE, 50)))
        assertEquals(1.0, p.multiplier(GrowthAxis.COOLDOWN, only(GrowthStat.LUCK, 100)))
    }

    @Test fun `every utility effect depends only on its declared stat regardless of primary`() {
        for (primary in GrowthStat.entries) for (secondary in GrowthStat.entries) {
            val p = GrowthProfile(primary, secondary)
            for ((axis, rule) in p.effects) for (stat in GrowthStat.entries) {
                val multiplier = p.multiplier(axis, only(stat, 20))
                if (stat == rule.stat) assertTrue(multiplier > 1.0, "$axis must respond to $stat")
                else assertEquals(1.0, multiplier, "$axis must not respond to $stat")
            }
        }
    }

    @Test fun `uncapped damage continues to grow far beyond the former three times limit`() {
        for (id in GrowthClassCatalog.styles.keys) {
            val p = GrowthProfile.forClass(id)
            for (axis in listOf(GrowthAxis.BASIC_DAMAGE, GrowthAxis.SKILL_DAMAGE)) {
                val a = p.multiplier(axis) { 10000 }
                val b = p.multiplier(axis) { 20000 }
                assertTrue(a > 3.0, "$id $axis")
                assertEquals((a - 1) * 2, b - 1, 0.000001)
                assertTrue(p.multiplier(axis) { Int.MAX_VALUE }.isFinite())
            }
        }
    }

    @Test fun `basic attack specialists outscale casters in basic damage not in skill damage`() {
        val basic = GrowthProfile.forClass("berserker")
        val caster = GrowthProfile.forClass("ice-wizard")
        val summon = GrowthProfile.forClass("swordplay")
        val stats: (GrowthStat) -> Int = { 50 }
        assertTrue(basic.multiplier(GrowthAxis.BASIC_DAMAGE, stats) > caster.multiplier(GrowthAxis.BASIC_DAMAGE, stats))
        assertTrue(caster.multiplier(GrowthAxis.SKILL_DAMAGE, stats) > basic.multiplier(GrowthAxis.SKILL_DAMAGE, stats))
        assertTrue(summon.multiplier(GrowthAxis.SKILL_DAMAGE, stats) > summon.multiplier(GrowthAxis.BASIC_DAMAGE, stats))
    }

    @Test fun `every playable class including copied and planetary abilities has an explicit style`() {
        val root = Path.of("src/main/kotlin/org/beobma/classWarPlugin/gameClass/list")
        val pattern = Regex("override val classId = \"([^\"]+)\"")
        val ids = Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.toList().flatMap {
                pattern.findAll(Files.readString(it)).map { match -> match.groupValues[1] }.toList()
            }.filter { it != "dummy" }.toSet()
        }
        assertEquals(ids, GrowthClassCatalog.styles.keys)
        ids.forEach { id -> GrowthAxis.entries.forEach { axis ->
            assertEquals(1.0, GrowthProfile.forClass(id).multiplier(axis) { 0 }, "$id $axis at zero stats")
        } }
    }

    @Test fun `every named class feature is wired to an implementation not just lore`() {
        val source = Files.walk(Path.of("src/main/kotlin/org/beobma/classWarPlugin/gameClass/list")).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.toList().associate { path ->
                val content = Files.readString(path)
                Regex("override val classId = \"([^\"]+)\"").find(content)?.groupValues?.get(1) to content
            }
        }
        GrowthClassCatalog.features.forEach { (id, features) ->
            assertTrue(id in GrowthClassCatalog.styles)
            for ((key, rule) in features) {
                assertContains(source.getValue(id), "\"$key\"", message = "$id/$key missing call site")
                assertEquals(10.0, rule.apply(10.0) { 0 })
                assertEquals(10.0, rule.apply(10.0) { -1 })
                assertTrue(rule.apply(10.0) { 100 } > 10.0)
                for (other in GrowthStat.entries.filter { it != rule.stat }) assertEquals(10.0, rule.apply(10.0, only(other, 100)))
            }
        }
    }

    @Test fun `swordplay summon thresholds distinguish agility passive from intellect infinite`() {
        val passive = GrowthClassCatalog.features.getValue("swordplay").getValue("passive-swords")
        val infinite = GrowthClassCatalog.features.getValue("swordplay").getValue("infinite-swords")
        assertEquals(3.0, passive.apply(3.0, only(GrowthStat.AGILITY, 19)))
        assertEquals(4.0, passive.apply(3.0, only(GrowthStat.AGILITY, 20)))
        assertEquals(3.0, passive.apply(3.0, only(GrowthStat.INTELLIGENCE, 100)))
        assertEquals(18.0, infinite.apply(18.0, only(GrowthStat.INTELLIGENCE, 9)))
        assertEquals(19.0, infinite.apply(18.0, only(GrowthStat.INTELLIGENCE, 10)))
        assertEquals(18.0, infinite.apply(18.0, only(GrowthStat.AGILITY, 100)))
        assertEquals(9.0, passive.apply(3.0) { Int.MAX_VALUE })
        assertEquals(36.0, infinite.apply(18.0) { Int.MAX_VALUE })
    }

    @Test fun `equipment changes recalculate feature thresholds without accumulating bonuses`() {
        val s = GrowthPlayerState()
        val settings = GrowthSettings()
        s.gain(500, settings)
        assertTrue(s.allocate(GrowthStat.AGILITY, 12))
        s.inventory.add("wind-charm")
        val passive = GrowthClassCatalog.features.getValue("swordplay").getValue("passive-swords")
        assertEquals(3.0, passive.apply(3.0, s::stat))
        assertTrue(s.equip("wind-charm"))
        assertEquals(4.0, passive.apply(3.0, s::stat))
        assertEquals(4.0, passive.apply(3.0, s::stat))
        assertTrue(s.equip("wind-charm"))
        assertEquals(3.0, passive.apply(3.0, s::stat))
    }

    @Test fun `utility caps remain bounded independently of uncapped damage`() {
        GrowthProfile.forClass("swordplay").effects.forEach { (_, rule) ->
            assertEquals(1.0 + rule.maximumBonus, rule.multiplier { Int.MAX_VALUE })
        }
    }

    @Test fun `configuration independently overrides damage slopes and utility stat bindings`() {
        val config = YamlConfiguration()
        config.set("basic-damage-weight", 0.1)
        config.set("skill-damage-weight", 2.0)
        config.set("effects.cooldown.stat", "LUCK")
        config.set("effects.cooldown.percent", 0.8)
        val p = GrowthProfile.read(config, GrowthProfile.forClass("swordplay"))
        assertEquals(1.0, p.multiplier(GrowthAxis.COOLDOWN, only(GrowthStat.INTELLIGENCE, 20)))
        assertEquals(1.16, p.multiplier(GrowthAxis.COOLDOWN, only(GrowthStat.LUCK, 20)), 0.000001)
        assertEquals(0.1, p.basicDamageWeight)
        assertEquals(2.0, p.skillDamageWeight)
        config.set("basic-damage-weight", Double.NaN)
        assertEquals(GrowthProfile.forClass("swordplay").basicDamageWeight,
            GrowthProfile.read(config, GrowthProfile.forClass("swordplay")).basicDamageWeight)
    }
}
