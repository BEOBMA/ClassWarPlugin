package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.damage.DamagePath
import kotlin.test.Test
import kotlin.test.assertEquals

class GameConfigurationDamageTest {
    @Test
    fun `defaults preserve every current damage value`() {
        val settings = GameConfiguration(startingItems = emptyList())

        DamageMultiplierType.entries.forEach { type ->
            assertEquals(1.0, settings.damageMultipliers[type])
            assertEquals(1.0, settings.damageMultiplier(type))
        }
    }

    @Test
    fun `overall and specific damage multipliers compose`() {
        val settings = GameConfiguration(
            startingItems = emptyList(),
            damageMultipliers = DamageMultiplierType.entries.associateWith { type ->
                when (type) {
                    DamageMultiplierType.OVERALL -> 1.5
                    DamageMultiplierType.FALL -> 0.4
                    else -> 1.0
                }
            },
        )

        assertEquals(0.6, settings.damageMultiplier(DamageMultiplierType.FALL), absoluteTolerance = 0.000001)
    }

    @Test
    fun `damage paths use their matching multiplier`() {
        val settings = GameConfiguration(
            startingItems = emptyList(),
            damageMultipliers = DamageMultiplierType.entries.associateWith { type ->
                if (type == DamageMultiplierType.BASIC_ATTACK) 0.0 else 1.0
            },
        )

        assertEquals(0.0, settings.damageMultiplier(DamagePath.BASIC_ATTACK))
        assertEquals(1.0, settings.damageMultiplier(DamagePath.SKILL))
    }
}
