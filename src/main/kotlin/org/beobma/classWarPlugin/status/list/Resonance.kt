package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.ResonanceMarkManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.status.StatusAbnormality

/** Exact resource thresholds, even in growth mode. These resources belong to the marked target. */
abstract class ResonanceResource : StatusAbnormality() {
    override val canRemove = true
    override val isClassMechanic = true
    override val isHarmful = true
    override val growsWithStats = false
    override var duration: Int? = 10

    override fun increasePower(amount: Int) {
        super.increasePower(amount)
        if (!applicationBlocked && amount > 0) refreshPair()
    }

    override fun updatePower(amount: Int) {
        val gained = amount > power
        super.updatePower(amount)
        if (!applicationBlocked && gained) refreshPair()
    }

    private fun refreshPair() {
        entityData.statusAbnormalitys.filterIsInstance<ResonanceResource>()
            .filter { it.power > 0 }.forEach { it.updateDuration(10) }
    }

    override fun onPowerChanged() {
        super.onPowerChanged()
        ResonanceMarkManager.update(entityData)
    }

    override fun onRemoveStatusAbnormality() {
        ResonanceMarkManager.update(entityData)
    }
}

class Aftermath : ResonanceResource() {
    override val name get() = Keyword.Aftermath.string
    override val description get() = listOf(Keyword.Aftermath.requireDescription())
    override var maxPower: Int? = 30

    private fun rejectAtResonanceCap(): Boolean {
        if (entityData.statusAbnormalitys.filterIsInstance<Resonance>().none { it.power >= 3 }) return false
        remove()
        return true
    }

    override fun increasePower(amount: Int) {
        if (applicationBlocked || rejectAtResonanceCap()) return
        super.increasePower(amount)
    }

    override fun updatePower(amount: Int) {
        if (applicationBlocked || rejectAtResonanceCap()) return
        super.updatePower(amount)
    }

    override fun onPowerChanged() {
        if (rejectAtResonanceCap()) return
        if (power >= 30) {
            entityData.getOrCreateStatus(casterData) { Resonance() }.increasePower(1)
            if (this in entityData.statusAbnormalitys) remove()
        } else super.onPowerChanged()
    }
}

class Resonance : ResonanceResource() {
    override val name get() = Keyword.Resonance.string
    override val description get() = listOf(Keyword.Resonance.requireDescription())
    override var maxPower: Int? = 3

    override fun onPowerChanged() {
        if (power >= 3) {
            entityData.statusAbnormalitys.filterIsInstance<Aftermath>().toList().forEach { it.remove() }
        }
        super.onPowerChanged()
    }

    /** Call only after a skill's use conditions pass; a failed consume grants no enhancement. */
    fun consume(amount: Int = 1): Boolean {
        if (applicationBlocked || amount <= 0 || power < amount) return false
        decreasePower(amount)
        (entity as? org.bukkit.entity.LivingEntity)?.let {
            org.beobma.classWarPlugin.manager.DamageIndicatorManager.showLabel(
                it, game.settings.damageIndicatorsEnabled,
                org.beobma.classWarPlugin.damage.DamageAppearance.RESONANCE)
        }
        return true
    }
}
