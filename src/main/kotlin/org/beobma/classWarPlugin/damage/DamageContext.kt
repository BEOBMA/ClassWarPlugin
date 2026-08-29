package org.beobma.classWarPlugin.damage

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.damageMultiplier
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.util.DamageType

/**
 * 한 번의 커스텀 피해 처리 과정에서 공유되는 가변 컨텍스트다.
 *
 * [originalDamage]에는 클래스 및 게임 배율이 생성 시점에 반영된다. 이후 처리기는
 * 기본 피해 보너스, 공격자 배율, 피격자 배율 순으로 [damage]를 재계산할 수 있다.
 * 고정 피해([DamageType.isFixed])에는 이 세 보정이 적용되지 않는다.
 *
 * @param baseDamage 배율 적용 전 피해량
 * @param bypassShield `true`면 [org.beobma.classWarPlugin.status.list.Shield]를 소모하지 않는다.
 * @param armorIgnoreRatio 방어력 계산에서 무시할 비율. 실제 계산 시 `0.0..1.0`으로 제한된다.
 */
class DamageContext(
    val attacker: PlayerData,
    val target: EntityData,
    val path: DamagePath,
    val damageType: DamageType,
    baseDamage: Double,
    val bypassShield: Boolean = false,
    val armorIgnoreRatio: Double = 0.0,
) {
    val originalDamage: Double = ClassBalanceManager.scaleDamage(attacker, path, baseDamage) *
        attacker.initGame.settings.damageMultiplier(path)
    var damage: Double = originalDamage
        private set
    var isCancelled: Boolean = false

    private var flatDamageBonus: Double = 0.0
    private var damageDealtMultiplier: Double = 1.0
    private var damageTakenMultiplier: Double = 1.0
    private var maximumDamage: Double? = null

    /** 배율 계산 전에 더할 고정 피해 보너스를 누적한다. */
    fun addBaseDamage(amount: Double) {
        if (damageType.isFixed) return
        flatDamageBonus += amount
        recalculateDamage()
    }

    /** 공격자 측 피해 배율을 기존 값에 곱한다. */
    fun addDamageDealtMultiplier(multiplier: Double) {
        if (damageType.isFixed) return
        damageDealtMultiplier *= multiplier
        recalculateDamage()
    }

    /** 피격자 측 피해 배율을 기존 값에 곱한다. */
    fun addDamageTakenMultiplier(multiplier: Double) {
        if (damageType.isFixed) return
        damageTakenMultiplier *= multiplier
        recalculateDamage()
    }

    internal fun applyShieldedDamage(remainingDamage: Double) {
        damage = remainingDamage.coerceAtLeast(0.0).coerceAtMost(maximumDamage ?: Double.MAX_VALUE)
    }

    /**
     * 현재와 이후의 피해량을 [maximum] 이하로 제한한다.
     * 여러 번 호출하면 가장 낮은 상한이 유지된다.
     */
    fun capDamage(maximum: Double) {
        val cappedMaximum = maximum.coerceAtLeast(0.0)
        val effectiveMaximum = minOf(maximumDamage ?: cappedMaximum, cappedMaximum)
        maximumDamage = effectiveMaximum
        damage = damage.coerceAtMost(effectiveMaximum)
    }

    private fun recalculateDamage() {
        damage = ((originalDamage + flatDamageBonus) * damageDealtMultiplier * damageTakenMultiplier)
            .coerceAtMost(maximumDamage ?: Double.MAX_VALUE)
    }
}
