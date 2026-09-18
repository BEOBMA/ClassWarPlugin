package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.game.*
import kotlin.test.*

class GrowthProgressionTest {
    @Test fun `multiple levels conserve points and experience then stop at cap`() {
        val state = GrowthPlayerState()
        val settings = GrowthSettings(maximumLevel = 4, experienceBase = 60, experienceStep = 25)
        assertEquals(2, state.gain(150, settings)); assertEquals(3, state.level)
        assertEquals(5, state.experience); assertEquals(6, state.points)
        assertFalse(state.allocate(GrowthStat.LUCK, -1)); assertFalse(state.allocate(GrowthStat.LUCK, 7))
        assertTrue(state.allocate(GrowthStat.LUCK, 6)); assertEquals(0, state.points)
        assertEquals(1, state.gain(Int.MAX_VALUE, settings)); assertEquals(4, state.level)
        assertEquals(0, state.experience); assertEquals(0, state.gain(500, settings))
    }
    @Test fun `equipment does not stack within a slot and cannot equip unowned ids`() {
        val s = GrowthPlayerState()
        assertFalse(s.equip("dawn-edge"))
        s.inventory.addAll(listOf("dawn-edge", "blood-fang"))
        assertTrue(s.equip("dawn-edge")); assertEquals(10, s.stat(GrowthStat.STRENGTH))
        assertTrue(s.equip("blood-fang")); assertEquals(5, s.stat(GrowthStat.STRENGTH))
        assertFalse(s.has(GrowthEffect.EXECUTE)); assertTrue(s.has(GrowthEffect.LIFESTEAL))
        assertTrue(s.equip("blood-fang")); assertEquals(0, s.stat(GrowthStat.STRENGTH))
    }
    @Test fun `scaling preserves base values and bounds crowd control and cooldown`() {
        val profile = GrowthProfile.forClass("ice-wizard")
        assertEquals(GrowthStat.INTELLIGENCE, profile.primary)
        GrowthAxis.entries.forEach { assertEquals(1.0, profile.multiplier(it) { 0 }) }
        assertTrue(profile.multiplier(GrowthAxis.BASIC_DAMAGE) { 10000 } > 3.0)
        assertTrue(profile.multiplier(GrowthAxis.SKILL_DAMAGE) { 10000 } > 3.0)
        assertEquals(1.5, profile.multiplier(GrowthAxis.DURATION) { 10000 })
        assertEquals(1.5, profile.multiplier(GrowthAxis.COOLDOWN) { 10000 })
        assertEquals(1.25, profile.multiplier(GrowthAxis.RANGE) { 10000 })
    }
    @Test fun `growth and every modifier roundtrip without changing classic persistence`() {
        val mode = MatchMode.GROWTH.toggled(MatchModifier.DUAL).toggled(MatchModifier.TEAM)
        assertTrue(mode.isGrowth); assertEquals(2, mode.assignedClassCount)
        assertEquals(mode, MatchMode.deserialize(mode.serialize()))
        assertEquals(MatchMode.CLASSIC, MatchMode.deserialize(""))
        assertEquals(MatchMode.DUAL, MatchMode.deserialize("DUAL"))
        assertNull(MatchMode.deserialize("UNKNOWN;DUAL"))
        assertNotNull(mode.validateRules(GameConfiguration(startingItems = emptyList(), fixedSpawnEnabled = true)))
        assertNull(mode.validateRules(GameConfiguration(startingItems = emptyList())))
    }
    @Test fun `item cooldown cannot be triggered twice in one combat tick`() {
        val s = GrowthPlayerState()
        assertTrue(s.trigger("a", 0, 6)); assertFalse(s.trigger("a", 0, 6)); assertFalse(s.trigger("a", 119, 6))
        assertTrue(s.trigger("a", 120, 6))
    }
    @Test fun `event definitions are timed and reference unique valid equipment`() {
        assertEquals(2, GrowthEventDefinition.defaults[0].phaseIndex)
        assertEquals(5, GrowthEventDefinition.defaults[1].phaseIndex)
        assertEquals(GrowthItems.all.size, GrowthItems.all.map { it.id }.distinct().size)
        GrowthEventDefinition.defaults.forEach { assertNotNull(GrowthItems.byId(it.reward)) }
    }
}
