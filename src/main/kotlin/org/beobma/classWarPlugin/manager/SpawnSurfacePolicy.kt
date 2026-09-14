package org.beobma.classWarPlugin.manager

/** 월드 접근 없이 지표 높이 표본을 판정해 협곡과 깊은 동굴 입구를 제외한다. */
internal object SpawnSurfacePolicy {
    fun isDeepDepression(candidateGroundY: Int, surroundingHeights: List<Int>, maximumDrop: Int): Boolean {
        if (surroundingHeights.isEmpty()) return false
        val sorted = surroundingHeights.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[middle - 1].toLong() + sorted[middle].toLong()) / 2L
        } else {
            sorted[middle].toLong()
        }
        return median - candidateGroundY.toLong() > maximumDrop.coerceAtLeast(0)
    }
}
