package org.beobma.classWarPlugin.util

import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffectType
import kotlin.math.min

object DamageCalculator {
    data class Result(val finalDamage: Double, val absorbed: Double)

    fun calculate(baseDamage: Double, target: LivingEntity, damageType: DamageType): Result {
        if (baseDamage <= 0.0) {
            return Result(0.0, 0.0)
        }

        if (damageType.isFixed) {
            return Result(baseDamage, 0.0)
        }

        var damage = baseDamage

        damage = applyArmorAndToughness(damage, target)
        damage = applyResistance(damage, target)

        damage = damage.coerceAtLeast(0.0)

        val absorptionAmount = target.absorptionAmount
        var absorbed = 0.0
        if (absorptionAmount > 0.0 && damage > 0.0) {
            absorbed = min(absorptionAmount, damage)
            target.absorptionAmount = (absorptionAmount - absorbed).coerceAtLeast(0.0)
            damage -= absorbed
        }

        return Result(damage, absorbed)
    }

    private fun applyArmorAndToughness(damage: Double, target: LivingEntity): Double {
        val armor = target.getAttribute(Attribute.ARMOR)?.value ?: 0.0
        val toughness = target.getAttribute(Attribute.ARMOR_TOUGHNESS)?.value ?: 0.0

        val armorFactor = (armor / 5.0).coerceAtLeast(armor - damage / (2.0 + toughness / 4.0))
        val damageMultiplier = 1.0 - (armorFactor.coerceAtMost(20.0) / 25.0)

        return damage * damageMultiplier
    }

    private fun applyResistance(damage: Double, target: LivingEntity): Double {
        val resistanceEffect = target.getPotionEffect(PotionEffectType.RESISTANCE) ?: return damage
        val amplifier = resistanceEffect.amplifier + 1
        val resistanceMultiplier = (1.0 - 0.2 * amplifier).coerceAtLeast(0.0)

        return damage * resistanceMultiplier
    }
}
