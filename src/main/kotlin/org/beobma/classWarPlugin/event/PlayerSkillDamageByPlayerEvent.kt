package org.beobma.classWarPlugin.event

import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class PlayerSkillDamageByPlayerEvent(
    baseDamage: Double,
    val damageType: DamageType,
    val damager: PlayerData,
    val entity: PlayerData
) : Event(), Cancellable {
    private var isCancelled = false
    private var baseDamage: Double = baseDamage
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
        flatDamageBonus += amount
        recalculateDamage()
    }

    fun addDamageDealtMultiplier(multiplier: Double) {
        damageDealtMultiplier *= multiplier
        recalculateDamage()
    }

    fun addDamageTakenMultiplier(multiplier: Double) {
        damageTakenMultiplier *= multiplier
        recalculateDamage()
    }

    private fun recalculateDamage() {
        damage = (baseDamage + flatDamageBonus) * damageDealtMultiplier * damageTakenMultiplier
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
