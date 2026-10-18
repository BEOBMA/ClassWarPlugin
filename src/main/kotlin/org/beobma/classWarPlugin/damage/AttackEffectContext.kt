package org.beobma.classWarPlugin.damage

import java.util.UUID

/** Carries readiness through synchronous redirected damage without changing projectile/skill damage. */
internal object AttackEffectContext {
    private data class State(val attacker: UUID, val allowed: Boolean)
    private val current = ThreadLocal<State?>()
    fun allowed(attacker: UUID, path: DamagePath): Boolean =
        path != DamagePath.BASIC_ATTACK || current.get()?.takeIf { it.attacker == attacker }?.allowed != false

    fun <T> with(attacker: UUID, allowed: Boolean, body: () -> T): T {
        val previous = current.get()
        current.set(State(attacker, allowed))
        try { return body() } finally { if (previous == null) current.remove() else current.set(previous) }
    }
}
