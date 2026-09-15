package org.beobma.classWarPlugin.growth

/** Apply the global cap in rounds so the first regions cannot consume every spawn slot. */
object GrowthPopulation {
    fun <T> distribute(regions: List<List<T>>, maximum: Int): List<T> = buildList {
        for (index in 0 until (regions.maxOfOrNull { it.size } ?: 0)) {
            for (region in regions) {
                if (size >= maximum.coerceAtLeast(0)) return@buildList
                region.getOrNull(index)?.let(::add)
            }
        }
    }
}
