package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.damage.DamageAppearance
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.entity.LivingEntity

/** Instant conversion of removed status power into one status-damage hit, independent of duration. */
class Settlement : StatusAbnormality() {
    override val name get() = Keyword.Settlement.string
    override val description get() = listOf(
        Keyword.Settlement.requireDescription(),
        "<gray>피해량: 제거한 상태이상 수치의 합. 남은 지속 시간은 반영하지 않는다.",
        "<gray>실제 연소만 있는 화상은 수치 1로 계산하며, 발동 후 결산은 사라진다.",
    )
    override val canRemove = true
    override val showPower = false
    override val showMaxPower = false
    override val showInActionBar = false
    override var maxPower: Int? = 1
    override var duration: Int? = 1

    override fun onPowerChanged() {
        if (power <= 0) { super.onPowerChanged(); return }
        val consumed = entityData.statusAbnormalitys.filter {
            it is Bleeding || it is Burn || it is Brightness || it is Frostbite
        }
        val living = entity as? LivingEntity
        val statusDamage = consumed.filterNot { it is Burn }.sumOf { contribution(it.power) }
        // Fire ticks and Burn describe the same fire, so never count them twice.
        val burnPower = burnContribution(living?.fireTicks ?: 0,
            consumed.filterIsInstance<Burn>().maxOfOrNull { it.power } ?: 0)
        remove()
        consumed.forEach { it.remove() }
        if (living != null) living.fireTicks = 0
        val total = statusDamage + burnPower
        if (total > 0.0) entityData.damage(total, DamageType.StatusAbnormality, casterData,
            appearance = DamageAppearance.SETTLEMENT)
    }

    companion object {
        internal fun contribution(power: Int): Double = power.coerceAtLeast(0).toDouble()
        internal fun burnContribution(fireTicks: Int, power: Int): Double =
            maxOf(if (fireTicks > 0) 1.0 else 0.0, contribution(power))
    }
}
