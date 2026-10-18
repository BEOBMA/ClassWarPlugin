package org.beobma.classWarPlugin.damage

/** Charge gates on-hit procs, not the vanilla damage of an early melee strike. */
internal object BasicAttackReadiness {
    fun ready(charge: Float): Boolean = charge.isFinite() && charge >= 1.0f
    private data class Snapshot(val tick: Int, val ready: Boolean)
    private val attacks = mutableMapOf<java.util.UUID, Snapshot>()
    fun record(attacker: java.util.UUID, tick: Int, charge: Float) {
        attacks.entries.removeIf { it.value.tick != tick }
        attacks[attacker] = Snapshot(tick, ready(charge))
    }
    fun effectsAllowed(attacker: java.util.UUID, tick: Int): Boolean = attacks[attacker]?.takeIf { it.tick == tick }?.ready ?: true
    fun clear(attacker: java.util.UUID) { attacks.remove(attacker) }
}
