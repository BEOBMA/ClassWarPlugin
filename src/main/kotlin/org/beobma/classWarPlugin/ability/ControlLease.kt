package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.entity.EntityStatus

/** A session can change its restrictions without restoring another session's snapshot. */
class ControlLease(scope: AbilityScope, private val status: EntityStatus) : AutoCloseable {
    private val locks = mutableMapOf<Control, AutoCloseable>()
    private var closed = false
    private val lifetime = scope.resources.own { release() }

    fun allow(control: Control, allowed: Boolean) {
        if (closed) return
        if (allowed) locks.remove(control)?.close()
        else locks.getOrPut(control) { status.controlLocks.acquire(control) }
    }

    override fun close() = lifetime.close()
    private fun release() {
        closed = true
        locks.values.forEach { it.close() }
        locks.clear()
    }
}
