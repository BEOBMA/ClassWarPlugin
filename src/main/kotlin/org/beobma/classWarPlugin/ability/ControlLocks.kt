package org.beobma.classWarPlugin.ability

enum class Control { MOVE, ATTACK, SKILL, ATTACKABLE, TARGETABLE }

class ControlLocks {
    private val locks = mutableMapOf<Any, Set<Control>>()
    fun blocks(control: Control): Boolean = locks.values.any { control in it }
    fun acquire(vararg controls: Control): AutoCloseable {
        val key = Any()
        locks[key] = controls.toSet()
        return AutoCloseable { locks.remove(key) }
    }
}
