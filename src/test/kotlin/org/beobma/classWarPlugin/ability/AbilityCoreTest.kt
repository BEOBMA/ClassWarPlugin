package org.beobma.classWarPlugin.ability

import kotlin.test.*

class AbilityCoreTest {
    @Test fun `cleanup runs once in reverse order even when another cleanup fails`() {
        val scope = ResourceScope()
        val calls = mutableListOf<Int>()
        scope.own { calls += 1 }
        scope.own { calls += 2; error("cleanup failure") }
        val released = scope.own { calls += 3 }
        released.close()
        assertFailsWith<IllegalStateException> { scope.close() }
        scope.close(); released.close()
        assertEquals(listOf(3, 2, 1), calls)
        assertEquals(0, scope.size)
        scope.own { calls += 4 }
        assertEquals(4, calls.last())
    }

    @Test fun `completed resources do not accumulate in their owner`() {
        val scope = ResourceScope()
        repeat(500) { scope.own(isAlive = { false }) { fail("already removed") } }
        scope.prune()
        assertEquals(0, scope.size)
        scope.close()
    }

    @Test fun `combat clock excludes pauses and handles server tick wrap`() {
        var serverTick = Int.MAX_VALUE.toLong() - 2
        val clock = GameClock { serverTick }
        serverTick += 2
        assertEquals(2L, clock.now())
        serverTick = Int.MIN_VALUE.toLong()
        assertEquals(3L, clock.now())
        clock.paused = true
        serverTick += 600
        clock.paused = false
        serverTick += 4
        assertEquals(7L, clock.now())
    }

    @Test fun `suspension preserves delayed and repeating task deadlines`() {
        val delay = EffectTimer(3, null)
        assertFalse(delay.advance(false))
        repeat(100) { assertFalse(delay.advance(true)) }
        assertFalse(delay.advance(false))
        assertTrue(delay.advance(false))
        assertTrue(delay.complete)
        assertFalse(delay.advance(false))
        val repeat = EffectTimer(0, 2)
        assertTrue(repeat.advance(false))
        assertFalse(repeat.advance(true))
        assertFalse(repeat.advance(false))
        assertTrue(repeat.advance(false))
    }

    @Test fun `dwarf and pluto restore in either removal order without resurrecting scale`() {
        for (dwarfFirst in listOf(true, false)) {
            val scale = ScalarModifiers(1.0, 0.0625)
            scale.set("dwarf", 0.3)
            scale.set("pluto", 0.05)
            assertEquals(0.0625, scale.value)
            scale.remove(if (dwarfFirst) "dwarf" else "pluto")
            assertEquals(if (dwarfFirst) 0.0625 else 0.3, scale.value)
            scale.remove(if (dwarfFirst) "pluto" else "dwarf")
            assertEquals(1.0, scale.value)
        }
    }

    @Test fun `permanent baseline changes survive temporary effects and caps`() {
        val health = ScalarModifiers(20.0, 1.0)
        health.set("dwarf", 0.7)
        health.set("ghost", 1.0, 1.0)
        health.base -= 5.0
        assertEquals(1.0, health.value)
        health.remove("ghost")
        assertEquals(10.5, health.value)
        health.remove("dwarf")
        assertEquals(15.0, health.value)
    }

    @Test fun `overlapping control locks only release their own restrictions`() {
        val status = object : org.beobma.classWarPlugin.entity.EntityStatus() {}
        val first = status.controlLocks.acquire(Control.MOVE, Control.ATTACK)
        val second = status.controlLocks.acquire(Control.MOVE, Control.SKILL)
        first.close(); first.close()
        assertFalse(status.canMove)
        assertTrue(status.canAttack)
        assertFalse(status.canSkillUse)
        status.canMove = false
        second.close()
        assertFalse(status.canMove)
        status.canMove = true
        assertTrue(status.canMove)
        assertTrue(status.canSkillUse)
    }
}
