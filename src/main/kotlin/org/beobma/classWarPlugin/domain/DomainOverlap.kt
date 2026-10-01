package org.beobma.classWarPlugin.domain

internal object DomainOverlap {
    fun intersects(dx: Double, dy: Double, dz: Double, a: Int, b: Int): Boolean =
        dx*dx + dy*dy + dz*dz < (a+b).toDouble()*(a+b)

    fun <T> component(seed: T, nodes: List<T>, overlaps: (T, T) -> Boolean): Set<T> {
        val result = linkedSetOf(seed)
        val queue = ArrayDeque<T>().apply { add(seed) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            nodes.filter { it !in result && overlaps(current,it) }.forEach { result += it; queue += it }
        }
        return result
    }
}
