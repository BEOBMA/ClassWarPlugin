package org.beobma.classWarPlugin.domain

/** Capture before the first replacement; never discard a snapshot until restoration succeeds. */
internal class DomainBlockLedger<K, V>(private val restoreState: (V) -> Boolean) {
    private val originals = linkedMapOf<K, V>()
    fun capture(key: K, snapshot: () -> V) { if (key !in originals) originals[key] = snapshot() }
    fun keys(): List<K> = originals.keys.toList()
    fun peek(key: K): V? = originals[key]
    /** Occupied cells stay in the ledger and can be retried without losing their original state. */
    fun restoreIf(key: K, permitted: (V) -> Boolean): Boolean {
        val snapshot = originals[key] ?: return true
        if (!permitted(snapshot)) return false
        restore(key)
        return true
    }
    fun restore(key: K) {
        val snapshot = originals[key] ?: return
        check(restoreState(snapshot)) { "영역 블록 복원 실패: $key" }
        originals.remove(key)
    }
}
