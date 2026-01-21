package org.beobma.classWarPlugin.util

import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.WeakHashMap

private data class DamageModifier(
    val baseDamage: Double,
    var flatBonus: Double = 0.0,
    var damageDealtMultiplier: Double = 1.0,
    var damageTakenMultiplier: Double = 1.0
) {
    fun calculate(): Double {
        return (baseDamage + flatBonus) * damageDealtMultiplier * damageTakenMultiplier
    }
}

private object DamageModifierTracker {
    private val modifiers = WeakHashMap<EntityDamageByEntityEvent, DamageModifier>()

    private fun getModifier(event: EntityDamageByEntityEvent): DamageModifier {
        return modifiers.getOrPut(event) { DamageModifier(event.damage) }
    }

    fun addBaseDamage(event: EntityDamageByEntityEvent, amount: Double) {
        val modifier = getModifier(event)
        modifier.flatBonus += amount
        event.damage = modifier.calculate()
    }

    fun addDamageDealtMultiplier(event: EntityDamageByEntityEvent, multiplier: Double) {
        val modifier = getModifier(event)
        modifier.damageDealtMultiplier *= multiplier
        event.damage = modifier.calculate()
    }

    fun addDamageTakenMultiplier(event: EntityDamageByEntityEvent, multiplier: Double) {
        val modifier = getModifier(event)
        modifier.damageTakenMultiplier *= multiplier
        event.damage = modifier.calculate()
    }
}

fun EntityDamageByEntityEvent.addBaseDamage(amount: Double) {
    DamageModifierTracker.addBaseDamage(this, amount)
}

fun EntityDamageByEntityEvent.addDamageDealtMultiplier(multiplier: Double) {
    DamageModifierTracker.addDamageDealtMultiplier(this, multiplier)
}

fun EntityDamageByEntityEvent.addDamageTakenMultiplier(multiplier: Double) {
    DamageModifierTracker.addDamageTakenMultiplier(this, multiplier)
}
