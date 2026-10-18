package org.beobma.classWarPlugin.gameClass.snakesword

import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.*
import kotlin.test.*

class WhipChainTest {
    @Test fun `grip is pinned and every link keeps its length during rapid turns`() {
        val chain = WhipChain(Vector(), Vector(0, 0, 1), 12.0)
        repeat(100) { tick ->
            val origin = Vector(tick*0.08, sin(tick*0.1)*0.2, 0.0)
            val direction = Vector(sin(tick*0.7), sin(tick*0.31), cos(tick*0.7)).normalize()
            val frame = chain.advance(origin, direction, 12.0)
            assertEquals(origin, frame.points.first())
            frame.points.zipWithNext().forEach { (a, b) -> assertEquals(0.75, a.distance(b), 1e-7) }
            assertTrue(frame.points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            assertTrue(frame.points.all { it.distance(origin) <= 12.00001 })
        }
    }

    @Test fun `a turn bends the chain and tip inertia lags behind the grip`() {
        val chain = WhipChain(Vector(), Vector(0, 0, 1), 12.0)
        val motion = chain.advance(Vector(), Vector(1, 0, 0), 12.0)
        val points = motion.points
        val grip = points[1].clone().subtract(points[0]).normalize()
        val tip = points.last().clone().subtract(points[points.lastIndex-1]).normalize()
        assertTrue(grip.x > tip.x + 0.1, "grip=$grip tip=$tip")
        assertTrue(points.zipWithNext().map { (a, b) -> b.clone().subtract(a).normalize() }
            .zipWithNext().any { (a, b) -> a.dot(b) < 0.999 })
        assertTrue(points.last().z > 1.0)
    }

    @Test fun `stopping the hand dissipates motion instead of dealing permanent idle damage`() {
        val chain = WhipChain(Vector(), Vector(0, 0, 1), 12.0)
        repeat(8) { chain.advance(Vector(), Vector(1, 0, 0), 12.0) }
        repeat(40) { chain.advance(Vector(), Vector(1, 0, 0), 12.0) }
        val motion = chain.advance(Vector(), Vector(1, 0, 0), 12.0)
        val largestMovement = motion.frames.zipWithNext().maxOf { (before, after) ->
            before.indices.maxOf { before[it].distance(after[it]) } * WhipChain.SUBSTEPS
        }
        assertEquals(0.0, SnakeSwordGeometry.speedDamage(largestMovement), "idle speed=$largestMovement")
        assertTrue(motion.points.last().x > 10.0)
    }

    @Test fun `basic swing reaches the front target while preserving a trailing flexible tip`() {
        fun direction(tick: Int): Vector {
            val t = (tick/12.0).coerceIn(0.0, 1.0)
            return Vector(0, 0, 1).rotateAroundY(Math.toRadians(-70.0 + 140.0*t*t*(3-2*t)))
        }
        val origin = Vector(0.0, 1.3, 0.0)
        val chain = WhipChain(origin, direction(0), 7.0)
        val target = BoundingBox(-0.3, 0.0, 4.0, 0.3, 1.8, 4.6)
        var hit = false
        for (tick in 1..16) {
            val motion = chain.advance(origin, direction(tick), 7.0)
            if (motion.contact(target) != null) hit = true
        }
        assertTrue(hit, "A physically trailing blade must still sweep through the original forward attack area")
    }

    @Test fun `extension keeps links connected as sword grows from the hand`() {
        val chain = WhipChain(Vector(), Vector(0, 0, 1), 0.5)
        repeat(12) { tick ->
            val length = min(12.0, 0.5+(tick+1)*1.15)
            val points = chain.advance(Vector(), Vector(0, 0, 1), length).points
            points.zipWithNext().forEach { (a, b) -> assertEquals(length/16, a.distance(b), 1e-7) }
        }
    }

    @Test fun `teleport rebases the sword without sweeping a damaging line across the world`() {
        val chain = WhipChain(Vector(), Vector(0, 0, 1), 12.0)
        val motion = chain.advance(Vector(100, 0, 0), Vector(0, 0, 1), 12.0)
        assertTrue(motion.rebased)
        assertEquals(Vector(100, 0, 0), motion.points.first())
        assertNull(motion.contact(BoundingBox(45.0, -1.0, -1.0, 55.0, 1.0, 15.0)))
    }

    @Test fun `substep sweeps retain an intermediate collision missed by the final pose`() {
        val frames = listOf(
            listOf(Vector(-2, 1, 0), Vector(-2, 1, 4)),
            listOf(Vector(2, 1, 0), Vector(2, 1, 4)),
            listOf(Vector(-2, 1, 0), Vector(-2, 1, 4)))
        val motion = WhipChain.Motion(frames)
        assertNotNull(motion.contact(BoundingBox(-0.3, 0.0, 1.7, 0.3, 1.8, 2.3)))
    }

    @Test fun `vertical aim full reversal and zero length stay finite`() {
        val chain = WhipChain(Vector(), Vector(0, 1, 0), 7.0)
        for (direction in listOf(Vector(0, -1, 0), Vector(0, 1, 0), Vector())) {
            repeat(12) {
                assertTrue(chain.advance(Vector(), direction, 7.0).points.all { p ->
                    p.x.isFinite() && p.y.isFinite() && p.z.isFinite()
                })
            }
        }
        val collapsed = chain.advance(Vector(), Vector(0, 0, 1), 0.0)
        assertTrue(collapsed.points.all { it == Vector() })
        assertNull(collapsed.contact(BoundingBox(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0)))
    }
}
