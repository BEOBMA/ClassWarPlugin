package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.damage.DamagePath
import kotlin.test.*

class GrowthCombatEquipmentTest {
    @Test fun `eight new families cover every ordered pair without duplicate builds`() {
        val items = GrowthCombatEquipment.items
        assertEquals(96, items.size)
        val families = items.groupBy { it.effect }
        assertEquals(8, families.size)
        val expected = GrowthStat.entries.flatMap { a -> GrowthStat.entries.filter { it != a }.map { a to it } }.toSet()
        for (family in families.values) {
            assertEquals(12, family.size)
            assertEquals(1, family.map { it.slot }.distinct().size)
            assertEquals(expected, family.map { item ->
                item.stats.entries.single { it.value == 9 }.key to item.stats.entries.single { it.value == 5 }.key
            }.toSet())
            assertTrue(family.all { it in GrowthItems.ordinary && !it.eventOnly })
        }
    }

    @Test fun `attack masteries affect only their declared damage paths`() {
        for (path in DamagePath.entries) {
            assertEquals(if (path.isBasicAttack) 1.12 else 1.0,
                outgoing(setOf(GrowthEffect.BASIC_MASTERY), path))
            assertEquals(if (path == DamagePath.SKILL) 1.12 else 1.0,
                outgoing(setOf(GrowthEffect.ARCANE_MASTERY), path))
            assertEquals(1.0, outgoing(setOf(GrowthEffect.AFFLICTION), path))
        }
        assertEquals(1.18, GrowthCombatEquipment.statusDamage { it == GrowthEffect.AFFLICTION })
        assertEquals(1.0, GrowthCombatEquipment.statusDamage { false })
    }

    @Test fun `health bonuses switch precisely at their thresholds`() {
        val ambush = setOf(GrowthEffect.AMBUSH)
        assertEquals(1.0, outgoing(ambush, targetHealth = 0.799999))
        assertEquals(1.12, outgoing(ambush, targetHealth = 0.8))
        val desperate = setOf(GrowthEffect.DESPERATION)
        assertEquals(1.0, outgoing(desperate, attackerHealth = 0.400001))
        assertEquals(1.15, outgoing(desperate, attackerHealth = 0.4))
        assertEquals(0.85, GrowthCombatEquipment.incoming({ it == GrowthEffect.LAST_STAND }, 0.4))
        assertEquals(1.0, GrowthCombatEquipment.incoming({ it == GrowthEffect.LAST_STAND }, 0.400001))
        assertEquals(0.88, GrowthCombatEquipment.incoming({ it == GrowthEffect.VANGUARD }, 0.8))
        assertEquals(1.0, GrowthCombatEquipment.incoming({ it == GrowthEffect.VANGUARD }, 0.799999))
    }

    @Test fun `slayer bonus excludes players and independent effects multiply once`() {
        val slayer = setOf(GrowthEffect.MONSTER_SLAYER)
        assertEquals(1.0, outgoing(slayer, creature = false))
        assertEquals(1.2, outgoing(slayer, creature = true))
        val effects = slayer + GrowthEffect.BASIC_MASTERY + GrowthEffect.DESPERATION
        assertEquals(1.12 * 1.2 * 1.15, outgoing(effects, attackerHealth = 0.4, creature = true), 1e-10)
        val state = GrowthPlayerState()
        val first = "duel-blade-onslaught"
        val second = "duel-blade-war"
        state.inventory.addAll(listOf(first, second))
        state.equip(first); state.equip(second)
        assertEquals(1, state.equipment.size)
        assertEquals(1.12, GrowthCombatEquipment.outgoing(state::has, DamagePath.BASIC_ATTACK, 1.0, 1.0, false))
        state.equip(second)
        assertEquals(1.0, GrowthCombatEquipment.outgoing(state::has, DamagePath.BASIC_ATTACK, 1.0, 1.0, false))
    }

    private fun outgoing(effects: Set<GrowthEffect>, path: DamagePath = DamagePath.BASIC_ATTACK,
        attackerHealth: Double = 1.0, targetHealth: Double = 1.0, creature: Boolean = false) =
        GrowthCombatEquipment.outgoing(effects::contains, path, attackerHealth, targetHealth, creature)
}
