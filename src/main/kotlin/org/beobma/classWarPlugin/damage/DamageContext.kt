package org.beobma.classWarPlugin.damage

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.util.DamageType

class DamageContext(
    val attacker: PlayerData,
    val target: EntityData,
    val path: DamagePath,
    val damageType: DamageType,
    baseDamage: Double,
    val bypassShield: Boolean = false,
) {
    val originalDamage: Double = baseDamage
    var damage: Double = baseDamage
        private set
    var isCancelled: Boolean = false

    private var flatDamageBonus: Double = 0.0
    private var damageDealtMultiplier: Double = 1.0
    private var damageTakenMultiplier: Double = 1.0

    fun addBaseDamage(amount: Double) {
        if (damageType.isFixed) return
        flatDamageBonus += amount
        recalculateDamage()
    }

    fun addDamageDealtMultiplier(multiplier: Double) {
        if (damageType.isFixed) return
        damageDealtMultiplier *= multiplier
        recalculateDamage()
    }

    fun addDamageTakenMultiplier(multiplier: Double) {
        if (damageType.isFixed) return
        damageTakenMultiplier *= multiplier
        recalculateDamage()
    }

    internal fun applyShieldedDamage(remainingDamage: Double) {
        damage = remainingDamage.coerceAtLeast(0.0)
    }

    private fun recalculateDamage() {
        damage = (originalDamage + flatDamageBonus) * damageDealtMultiplier * damageTakenMultiplier
    }
}
