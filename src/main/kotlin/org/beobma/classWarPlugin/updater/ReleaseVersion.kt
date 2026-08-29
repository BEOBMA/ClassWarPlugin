package org.beobma.classWarPlugin.updater

/** A small SemVer-compatible comparator for GitHub release tags and plugin versions. */
internal class ReleaseVersion private constructor(
    private val numbers: List<Int>,
    private val preRelease: List<String>,
) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int {
        val numberCount = maxOf(numbers.size, other.numbers.size)
        repeat(numberCount) { index ->
            val comparison = (numbers.getOrNull(index) ?: 0).compareTo(other.numbers.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }

        if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
        if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1

        val preReleaseCount = maxOf(preRelease.size, other.preRelease.size)
        repeat(preReleaseCount) { index ->
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right, ignoreCase = true)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private val VERSION_PATTERN = Regex(
            pattern = "^(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$",
        )

        fun parse(value: String): ReleaseVersion? {
            val normalized = value.trim().removePrefix("v").removePrefix("V")
            val match = VERSION_PATTERN.matchEntire(normalized) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            val preRelease = match.groupValues[2]
                .takeIf { it.isNotEmpty() }
                ?.split('.')
                .orEmpty()
            return ReleaseVersion(numbers, preRelease)
        }
    }
}
