package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality

/**
 * 여러 클래스가 공유할 수 있는 범용 충전 자원이다.
 *
 * 충전 획득 조건과 소비 효과는 각 클래스가 결정한다. 기본 최대치는 100이며,
 * [configureMaximum]에 `null`을 전달하면 최대치가 없는 충전 자원으로 사용할 수 있다.
 */
class Charge(maximum: Int? = DEFAULT_MAXIMUM) : StatusAbnormality() {
    companion object {
        const val DEFAULT_MAXIMUM = 100
    }

    override val name = Keyword.Charge.string
    override val description = listOf(
        Keyword.Charge.description ?: "",
        "",
        "<dark_gray>클래스에 따라 최대치와 획득 및 소비 조건이 달라진다.",
    )
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 0
    override var maxPower: Int? = validatedMaximum(maximum)
    override val showMaxPower = true
    override var duration: Int? = null

    val charge: Int
        get() = power

    fun configureMaximum(maximum: Int?) {
        maxPower = validatedMaximum(maximum)
        val cappedPower = maxPower?.let { power.coerceAtMost(it) } ?: power
        updatePower(cappedPower)
    }

    fun addCharge(amount: Int): Int {
        require(amount >= 0) { "Charge amount must not be negative." }
        if (amount == 0) return power
        val nextPower = (power.toLong() + amount.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        updatePower(nextPower)
        return power
    }

    fun hasCharge(amount: Int): Boolean {
        require(amount >= 0) { "Required charge must not be negative." }
        return power >= amount
    }

    /** 필요한 충전량이 모두 있을 때에만 소비한다. */
    fun consumeCharge(amount: Int): Boolean {
        require(amount >= 0) { "Consumed charge must not be negative." }
        if (!hasCharge(amount)) return false
        if (amount > 0) updatePower(power - amount)
        return true
    }

    /** 현재 충전량에서 [maximumAmount]까지 소비하고 실제 소비량을 반환한다. */
    fun consumeUpTo(maximumAmount: Int): Int {
        require(maximumAmount >= 0) { "Maximum consumed charge must not be negative." }
        val consumed = power.coerceAtMost(maximumAmount)
        if (consumed > 0) updatePower(power - consumed)
        return consumed
    }

    fun consumeAll(): Int {
        val consumed = power
        if (consumed > 0) updatePower(0)
        return consumed
    }

    fun clearCharge() {
        if (power > 0) updatePower(0)
    }

    override fun actionBarText(): String {
        val maximumText = maxPower?.let { "<dark_gray>/</dark_gray><aqua>$it</aqua>" } ?: ""
        return "$name: <aqua><bold>$power</bold></aqua>$maximumText"
    }

    private fun validatedMaximum(maximum: Int?): Int? {
        require(maximum == null || maximum >= 0) { "Maximum charge must not be negative." }
        return maximum
    }
}
