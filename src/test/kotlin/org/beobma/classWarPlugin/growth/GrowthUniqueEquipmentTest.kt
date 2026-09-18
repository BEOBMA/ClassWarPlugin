package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.damage.DamagePath
import kotlin.test.*

class GrowthUniqueEquipmentTest {
    @Test fun `every new effect has working positive negative and unequipped cases`() {
        val cases = listOf(
            Triple(GrowthEffect.MELEE_FURY, GrowthCombatFacts(path = DamagePath.BASIC_ATTACK), GrowthCombatFacts(path = DamagePath.RANGED_ATTACK)),
            Triple(GrowthEffect.DEADEYE, GrowthCombatFacts(path = DamagePath.RANGED_ATTACK), GrowthCombatFacts(path = DamagePath.SKILL)),
            Triple(GrowthEffect.CLOSE_CASTER, GrowthCombatFacts(path = DamagePath.SKILL, distance = 4.0), GrowthCombatFacts(path = DamagePath.SKILL, distance = 4.01)),
            Triple(GrowthEffect.FAR_CASTER, GrowthCombatFacts(path = DamagePath.SKILL, distance = 8.0), GrowthCombatFacts(path = DamagePath.SKILL, distance = 7.99)),
            Triple(GrowthEffect.HEALTHY_HUNTER, GrowthCombatFacts(attackerHealth = 0.8, targetMob = true), GrowthCombatFacts(attackerHealth = 0.799, targetMob = true)),
            Triple(GrowthEffect.DUEL_PREDATOR, GrowthCombatFacts(targetPlayer = true), GrowthCombatFacts(targetMob = true)),
            Triple(GrowthEffect.MELEE_GUARD, GrowthCombatFacts(path = DamagePath.BASIC_ATTACK), GrowthCombatFacts(path = DamagePath.RANGED_ATTACK)),
            Triple(GrowthEffect.MISSILE_GUARD, GrowthCombatFacts(path = DamagePath.RANGED_ATTACK), GrowthCombatFacts(path = DamagePath.BASIC_ATTACK)),
            Triple(GrowthEffect.SPELL_GUARD, GrowthCombatFacts(path = DamagePath.SKILL), GrowthCombatFacts(path = DamagePath.STATUS_EFFECT)),
            Triple(GrowthEffect.BEAST_GUARD, GrowthCombatFacts(attackerMob = true), GrowthCombatFacts(attackerMob = false)),
            Triple(GrowthEffect.CLOSE_GUARD, GrowthCombatFacts(distance = 3.0), GrowthCombatFacts(distance = 3.01)),
            Triple(GrowthEffect.DISTANT_GUARD, GrowthCombatFacts(distance = 8.0), GrowthCombatFacts(distance = 7.99)),
            Triple(GrowthEffect.GIANT_HUNTER, GrowthCombatFacts(targetMax = 25.0), GrowthCombatFacts(targetMax = 24.99)),
            Triple(GrowthEffect.UNDERDOG, GrowthCombatFacts(attackerHealth = 0.599), GrowthCombatFacts(attackerHealth = 0.6)),
            Triple(GrowthEffect.FINISHER_SKILL, GrowthCombatFacts(path = DamagePath.SKILL, targetHealth = 0.3), GrowthCombatFacts(path = DamagePath.SKILL, targetHealth = 0.301)),
            Triple(GrowthEffect.STEADY_AIM, GrowthCombatFacts(path = DamagePath.RANGED_ATTACK), GrowthCombatFacts(path = DamagePath.RANGED_ATTACK, attackerSprinting = true)),
            Triple(GrowthEffect.SPRINT_STRIKE, GrowthCombatFacts(attackerSprinting = true), GrowthCombatFacts(attackerSprinting = false)),
            Triple(GrowthEffect.AIR_ASSAULT, GrowthCombatFacts(attackerGrounded = false), GrowthCombatFacts(attackerGrounded = true)),
            Triple(GrowthEffect.NIGHT_GUARD, GrowthCombatFacts(night = true), GrowthCombatFacts(night = false)),
            Triple(GrowthEffect.DAY_GUARD, GrowthCombatFacts(night = false), GrowthCombatFacts(night = true)),
            Triple(GrowthEffect.CROUCH_GUARD, GrowthCombatFacts(targetSneaking = true), GrowthCombatFacts(targetSneaking = false)),
            Triple(GrowthEffect.AIR_GUARD, GrowthCombatFacts(targetGrounded = false), GrowthCombatFacts(targetGrounded = true)),
            Triple(GrowthEffect.EVEN_GUARD, GrowthCombatFacts(targetHealth = 0.6), GrowthCombatFacts(targetHealth = 0.4)),
            Triple(GrowthEffect.GIANT_GUARD, GrowthCombatFacts(attackerMax = 25.0), GrowthCombatFacts(attackerMax = 24.99)),
        )
        assertEquals(GrowthUniqueEquipment.rules.map { it.effect }.toSet(), cases.map { it.first }.toSet())
        for ((effect, active, inactive) in cases) {
            val rule = GrowthUniqueEquipment.rules.single { it.effect == effect }
            val evaluate = if (rule.offensive) GrowthUniqueEquipment::outgoing else GrowthUniqueEquipment::incoming
            assertEquals(rule.multiplier, evaluate({ it == effect }, active), 1e-9, effect.name)
            assertEquals(1.0, evaluate({ it == effect }, inactive), 1e-9, effect.name)
            assertEquals(1.0, evaluate({ false }, active), 1e-9, effect.name)
            val opposite = if (rule.offensive) GrowthUniqueEquipment::incoming else GrowthUniqueEquipment::outgoing
            assertEquals(1.0, opposite({ it == effect }, active), 1e-9, effect.name)
        }
    }

    @Test fun `each unique effect has twenty one builds in every rarity and real descriptions`() {
        assertEquals(44, GrowthEffect.entries.size)
        assertEquals(24, GrowthUniqueEquipment.rules.size)
        assertEquals(504, GrowthUniqueEquipment.items.size)
        for (rule in GrowthUniqueEquipment.rules) {
            val equipment = GrowthItems.all.filter { it.effect == rule.effect }
            assertEquals(63, equipment.size)
            GrowthRarity.entries.forEach { rarity -> assertEquals(21, equipment.count { it.rarity == rarity }) }
            assertTrue(equipment.all { it.description.contains(rule.description) && it.slot == rule.slot })
        }
    }

    @Test fun `range and stance bonuses require the right attack path`() {
        assertEquals(1.0, GrowthUniqueEquipment.outgoing({ it == GrowthEffect.AIR_ASSAULT },
            GrowthCombatFacts(path = DamagePath.SKILL, attackerGrounded = false)))
        assertEquals(1.0, GrowthUniqueEquipment.outgoing({ it == GrowthEffect.STEADY_AIM },
            GrowthCombatFacts(path = DamagePath.RANGED_ATTACK, attackerGrounded = false)))
        assertEquals(1.0, GrowthUniqueEquipment.incoming({ it == GrowthEffect.EVEN_GUARD },
            GrowthCombatFacts(targetHealth = 0.8)))
    }
}

