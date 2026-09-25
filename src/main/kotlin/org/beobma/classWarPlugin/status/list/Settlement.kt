package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.damage.DamageAppearance
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.entity.LivingEntity

/** Instant conversion of remaining status power and time into one status-damage hit. */
class Settlement : StatusAbnormality() {
    override val name get() = Keyword.Settlement.string
    override val description get() = listOf(
        Keyword.Settlement.requireDescription(),
        "<gray>피해량: 제거한 수치 × 남은 초의 합. 무기한 상태는 1초로 계산한다.",
        "<gray>화상은 실제 남은 연소 시간을 반영하며, 발동 후 결산은 사라진다.",
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
        val statusDamage = consumed.filterNot { it is Burn }.sumOf { contribution(it.power, it.duration) }
        // Fire ticks and Burn describe the same fire, so never count them twice.
        val burnSeconds = maxOf(
            (living?.fireTicks ?: 0).coerceAtLeast(0) / 20.0,
            consumed.filterIsInstance<Burn>().maxOfOrNull { contribution(it.power, it.duration) } ?: 0.0,
        )
        remove()
        consumed.forEach { it.remove() }
        if (living != null) living.fireTicks = 0
        val total = statusDamage + burnSeconds
        if (total > 0.0) entityData.damage(total, DamageType.StatusAbnormality, casterData,
            appearance = DamageAppearance.SETTLEMENT)
    }

    companion object {
        internal fun contribution(power: Int, seconds: Int?): Double =
            power.coerceAtLeast(0).toDouble() * (seconds ?: 1).coerceAtLeast(0)
    }
}
