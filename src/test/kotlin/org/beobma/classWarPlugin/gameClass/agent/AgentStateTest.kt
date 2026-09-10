package org.beobma.classWarPlugin.gameClass.agent

import java.util.UUID
import kotlin.test.*

class AgentStateTest {
    private val a = UUID(0, 1)
    private val b = UUID(0, 2)
    private fun at(order: Int): AgentState = AgentState().also { state -> repeat(order) { state.begin(100, a, 2) } }
    private fun AgentState.hitTarget(id: UUID = a, basic: Boolean = true, skill: Boolean = false,
        weapon: Int = 2, behind: Boolean = false, sprinting: Boolean = false) = hit(id, basic, skill, weapon, behind, sprinting)

    @Test fun `three hits and ten second transformation reset independently`() {
        val state = AgentState(); state.reset(100)
        assertEquals(300, state.nextMorph)
        assertFalse(state.basicHit()); assertFalse(state.basicHit()); assertTrue(state.basicHit())
        state.morphed(130)
        assertEquals(0, state.basicHits); assertEquals(330, state.nextMorph)
    }
    @Test fun `designated enemy and distinct enemy orders enforce their requirements`() {
        val one = at(1); one.hitTarget(b); assertNull(one.result(150))
        one.hitTarget(a); assertEquals(true, one.result(150))
        val two = at(2); two.hitTarget(); two.hitTarget(); assertNull(two.result(150))
        two.hitTarget(b); assertEquals(true, two.result(150))
    }
    @Test fun `skill only order waits six seconds and rejects basic swings`() {
        val state = at(3); state.hitTarget(basic = false, skill = true)
        assertNull(state.result(219)); assertEquals(true, state.result(220))
        val failed = at(3); failed.hitTarget(basic = false, skill = true); failed.basicUsed()
        assertEquals(false, failed.result(110))
        assertEquals(false, at(3).result(220))
    }
    @Test fun `weapon highest health combo back sprint and skill objectives`() {
        val weapon = at(4); weapon.hitTarget(weapon = 1); assertNull(weapon.result(150))
        weapon.hitTarget(); assertEquals(true, weapon.result(150))
        val health = at(5); health.hitTarget(b); assertNull(health.result(150)); health.hitTarget(); assertEquals(true, health.result(150))
        val combo = at(6); repeat(2) { combo.hitTarget() }; assertNull(combo.result(150)); combo.hitTarget(); assertEquals(true, combo.result(150))
        val back = at(7); back.hitTarget(); assertNull(back.result(150)); back.hitTarget(behind = true); assertEquals(true, back.result(150))
        val sprint = at(8); sprint.hitTarget(); assertNull(sprint.result(150)); sprint.hitTarget(sprinting = true); assertEquals(true, sprint.result(150))
        val skill = at(9); skill.hitTarget(); assertNull(skill.result(150)); skill.hitTarget(basic = false, skill = true); assertEquals(true, skill.result(150))
    }
    @Test fun `last valid tick success survives evaluation on deadline`() {
        val state = at(1); state.hitTarget()
        assertEquals(true, state.result(state.deadline))
        assertEquals(false, at(1).result(300))
        assertEquals(false, at(2).result(260))
        assertEquals(false, at(4).result(500))
    }
    @Test fun `unlimited final directive and rewards are resolved once`() {
        val state = at(10)
        assertEquals(Long.MAX_VALUE, state.deadline)
        assertNull(state.result(9999999)); assertEquals(true, state.result(9999999, true))
        state.resolve(true); state.resolve(true)
        assertEquals(1, state.successes); assertNull(state.result(9999999, true))
        state.begin(200, a, 0); assertEquals(1, state.directive)
    }
    @Test fun `permanent modifiers accumulate additively and reset for a new game`() {
        val state = at(1)
        state.resolve(true); state.begin(100, a, 0); state.resolve(false)
        assertEquals(1.15, state.dealtMultiplier, .0001); assertEquals(.95, state.takenMultiplier, .0001)
        repeat(30) { state.begin(100, a, 0); state.resolve(false) }
        assertEquals(.5, state.dealtMultiplier)
        assertEquals(1.5, state.takenMultiplier)
        state.reset(500); assertEquals(1.0, state.dealtMultiplier); assertEquals(1.0, state.takenMultiplier)
        assertEquals(0, state.directive); assertEquals(700, state.deadline)
    }
    @Test fun `greatsword remains usable and rapier achieves intended total speed`() {
        assertEquals(.88, 4.0 * AgentWeaponStats.swordSpeedMultiplier(4.0, .55) - 2.4, .0001)
        assertEquals(4.0, 4.0 * AgentWeaponStats.swordSpeedMultiplier(4.0, 2.5) - 2.4, .0001)
        assertEquals(1.0, AgentWeaponStats.swordSpeedMultiplier(4.0, 1.0))
    }
    @Test fun `failed directive restarts the same stage with a fresh deadline and progress`() {
        val state = at(2)
        state.hitTarget(a)
        assertEquals(false, state.result(260))
        state.resolve(false)
        assertEquals(2, state.nextDirective)
        state.begin(270, a, 2)
        assertEquals(2, state.directive)
        assertEquals(430L, state.deadline)
        assertEquals(0, state.progress)
        assertFalse(state.resolved)
        state.hitTarget(a); state.hitTarget(b)
        assertEquals(true, state.result(300))
        state.resolve(true)
        assertEquals(3, state.nextDirective)
        state.begin(301, a, 2)
        assertEquals(3, state.directive)
    }
    @Test fun `failure cap does not accumulate hidden damage debt`() {
        val state = at(1)
        repeat(30) { state.resolve(false); state.begin(100L + it, a, 2) }
        assertEquals(1, state.directive)
        assertEquals(50, state.dealtPercent)
        assertEquals(150, state.takenPercent)
        state.resolve(true)
        assertEquals(70, state.dealtPercent)
        assertEquals(140, state.takenPercent)
        state.resolve(true)
        assertEquals(70, state.dealtPercent)
        assertEquals(140, state.takenPercent)
    }
    @Test fun `success adds twenty dealt and removes ten taken without negative damage`() {
        val state = at(1)
        state.resolve(true)
        assertEquals(120, state.dealtPercent)
        assertEquals(90, state.takenPercent)
        repeat(12) { state.begin(200L + it, a, 0); state.resolve(true) }
        assertEquals(360, state.dealtPercent)
        assertEquals(0, state.takenPercent)
        state.begin(300, a, 0); state.resolve(false)
        assertEquals(355, state.dealtPercent)
        assertEquals(5, state.takenPercent)
    }
}
