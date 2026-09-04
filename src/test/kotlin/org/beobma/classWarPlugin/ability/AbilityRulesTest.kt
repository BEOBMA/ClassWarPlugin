package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.gameClass.math.MathProblemGenerator
import org.beobma.classWarPlugin.gameClass.referee.*
import org.beobma.classWarPlugin.gameClass.swordplay.SwordGeometry
import kotlin.random.Random
import kotlin.test.*
import kotlin.math.PI

class AbilityRulesTest {
    @Test fun `question generation is deterministic for every difficulty without a server`() {
        val first = MathProblemGenerator(Random(154))
        val second = MathProblemGenerator(Random(154))
        for (level in 1..MathProblemGenerator.MAX_DIFFICULTY) {
            repeat(50) {
                val question = first.generate(level)
                assertEquals(question, second.generate(level))
                assertTrue(question.first.isNotBlank())
            }
            assertTrue(first.timeLimitSeconds(level) in 15..30)
        }
    }

    @Test fun `basic arithmetic returns the calculated answer`() {
        val minimum = object : Random() { override fun nextBits(bitCount: Int) = 0 }
        assertEquals("10 + 5 = ?" to 15, MathProblemGenerator(minimum).generate(1))
    }

    @Test fun `each crime has one truth eight distinct lies and one admission`() {
        for (crime in Crime.entries) repeat(25) { seed ->
            val options = DefenseCatalog.options(crime, Random(seed))
            assertEquals(10, options.size)
            assertEquals(10, options.map { it.text }.distinct().size)
            assertEquals(1, options.count { it.kind == DefenseKind.TRUTH })
            assertEquals(8, options.count { it.kind == DefenseKind.LIE })
            assertEquals(1, options.count { it.kind == DefenseKind.ADMISSION })
            assertEquals(crime, Crime.fromInput(" ${crime.displayName} "))
        }
    }

    @Test fun `tilted orbits preserve radius under rotation and wrap negative angles`() {
        for (step in 0..24) {
            val angle = step * PI / 12
            assertEquals(3.0, SwordGeometry.tiltedOrbitOffset(angle, 3.0, 0.75, 1.3).length(), 1e-9)
            assertEquals(SwordGeometry.normalizeOrbitAngle(angle), SwordGeometry.normalizeOrbitAngle(angle - 2 * PI), 1e-9)
        }
    }
}
