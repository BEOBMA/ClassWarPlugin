@file:Suppress("DEPRECATION")

package org.beobma.classWarPlugin.damage

import org.beobma.classWarPlugin.util.DamageCalculator
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier

/** Keeps vanilla blocking, resistance, enchantments and absorption on native melee attacks. */
internal object VanillaArmorIgnore {
    fun apply(event: EntityDamageEvent, target: LivingEntity, ratio: Double) {
        if (!ratio.isFinite() || ratio <= 0.0 || !event.isApplicable(DamageModifier.ARMOR)) return
        val beforeArmor = DamageModifier.entries.takeWhile { it != DamageModifier.ARMOR }
            .sumOf { event.getDamage(it) }.coerceAtLeast(0.0)
        if (beforeArmor <= 0.0) return
        val fullReduction = beforeArmor - DamageCalculator.applyArmorAndToughness(beforeArmor, target, 0.0)
        if (fullReduction <= 0.0) return
        val reducedReduction = beforeArmor - DamageCalculator.applyArmorAndToughness(beforeArmor, target, ratio)
        applyModifiers(event, beforeArmor, reducedReduction / fullReduction, target.absorptionAmount)
    }

    internal fun applyModifiers(event: EntityDamageEvent, beforeArmor: Double, reductionRatio: Double, absorption: Double) {
        val oldArmor = event.getDamage(DamageModifier.ARMOR)
        val newArmor = oldArmor * reductionRatio.coerceIn(0.0, 1.0)
        event.setDamage(DamageModifier.ARMOR, newArmor)
        var oldRemaining = (beforeArmor + oldArmor).coerceAtLeast(0.0)
        var newRemaining = (beforeArmor + newArmor).coerceAtLeast(0.0)
        for (modifier in listOf(DamageModifier.RESISTANCE, DamageModifier.MAGIC, DamageModifier.ABSORPTION)) {
            if (!event.isApplicable(modifier)) continue
            val old = event.getDamage(modifier)
            val next = if (modifier == DamageModifier.ABSORPTION) -minOf(absorption, newRemaining)
                else if (oldRemaining > 0.0) old * newRemaining / oldRemaining else 0.0
            event.setDamage(modifier, next)
            oldRemaining = (oldRemaining + old).coerceAtLeast(0.0)
            newRemaining = (newRemaining + next).coerceAtLeast(0.0)
        }
    }
}
