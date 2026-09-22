package org.beobma.classWarPlugin.domain

/** Session-owned presentation. Implementations must not start unowned timers. */
interface DomainPresentation {
    fun start()
    fun formation(progress: Double)
    fun reveal(subtitle: Boolean)
    fun activate()
    fun sustain(tick: Int)
    fun beginDissolve()
    fun dissolve(tick: Int)
}
