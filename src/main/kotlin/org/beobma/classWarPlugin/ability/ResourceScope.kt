package org.beobma.classWarPlugin.ability

/** Owns reversible effects. Closing or releasing a handle executes its cleanup exactly once. */
class ResourceScope : AutoCloseable {
    private val entries = linkedSetOf<Handle>()
    private var sequence = 0L
    fun checkpoint(): Long = sequence
    /** Undo still-live effects created after a recorded frame without closing the ability itself. */
    fun rewind(checkpoint: Long) {
        entries.toList().asReversed().filter { it.sequence > checkpoint }.forEach { it.close() }
    }
    var isClosed = false
        private set
    val size: Int get() = entries.size

    fun own(isAlive: () -> Boolean = { true }, cleanup: () -> Unit): Handle {
        val handle = Handle(isAlive, cleanup)
        if (isClosed) handle.close() else entries += handle
        return handle
    }

    fun prune() = entries.toList().filterNot { it.isAlive() }.forEach { it.forget() }

    override fun close() {
        if (isClosed) return
        isClosed = true
        var failure: Throwable? = null
        entries.toList().asReversed().forEach {
            try { it.close() } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    inner class Handle internal constructor(internal val isAlive: () -> Boolean, private val cleanup: () -> Unit) : AutoCloseable {
        internal val sequence = ++this@ResourceScope.sequence
        private var released = false
        fun forget() { released = true; entries.remove(this) }
        override fun close() {
            if (released) return
            forget()
            cleanup()
        }
    }
}
