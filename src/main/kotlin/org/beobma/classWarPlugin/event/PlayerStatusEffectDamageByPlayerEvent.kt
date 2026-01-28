package org.beobma.classWarPlugin.event

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class PlayerStatusEffectDamageByPlayerEvent(
    baseDamage: Double,
    val damageType: DamageType,
    val damager: PlayerData,
    val entity: EntityData
) : Event(), Cancellable {
    private var isCancelled = false
    private val baseDamage: Double = baseDamage
    private var flatDamageBonus: Double = 0.0
    private var damageDealtMultiplier: Double = 1.0
    private var damageTakenMultiplier: Double = 1.0

    var damage: Double = baseDamage
        private set

    override fun isCancelled(): Boolean {
        return isCancelled
    }

    override fun setCancelled(cancel: Boolean) {
        isCancelled = cancel
    }

    fun addBaseDamage(amount: Double) {
        if (damageType.isFixed) {
            return
        }
        flatDamageBonus += amount
        recalculateDamage()
    }

    fun addDamageDealtMultiplier(multiplier: Double) {
        if (damageType.isFixed) {
            return
        }
        damageDealtMultiplier *= multiplier
        recalculateDamage()
    }

    fun addDamageTakenMultiplier(multiplier: Double) {
        if (damageType.isFixed) {
            return
        }
        damageTakenMultiplier *= multiplier
        recalculateDamage()
    }

    private fun recalculateDamage() {
        damage = if (damageType.isFixed) {
            baseDamage
        } else {
            (baseDamage + flatDamageBonus) * damageDealtMultiplier * damageTakenMultiplier
        }
    }

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
}
