package org.beobma.classWarPlugin.gameClass.writer

/** Characters outside the longest common subsequence are the mistyped, omitted, or added parts. */
internal data class TypingDifference(
    val expected: String,
    val input: String,
    val expectedCorrect: BooleanArray,
    val inputCorrect: BooleanArray,
) {
    companion object {
        fun compare(expected: String, input: String): TypingDifference {
            val rows = expected.length + 1
            val columns = input.length + 1
            val lcs = Array(rows) { IntArray(columns) }
            for (expectedIndex in expected.indices.reversed()) {
                for (inputIndex in input.indices.reversed()) {
                    lcs[expectedIndex][inputIndex] = if (expected[expectedIndex] == input[inputIndex]) {
                        lcs[expectedIndex + 1][inputIndex + 1] + 1
                    } else {
                        maxOf(lcs[expectedIndex + 1][inputIndex], lcs[expectedIndex][inputIndex + 1])
                    }
                }
            }

            val expectedCorrect = BooleanArray(expected.length)
            val inputCorrect = BooleanArray(input.length)
            var expectedIndex = 0
            var inputIndex = 0
            while (expectedIndex < expected.length && inputIndex < input.length) {
                when {
                    expected[expectedIndex] == input[inputIndex] -> {
                        expectedCorrect[expectedIndex++] = true
                        inputCorrect[inputIndex++] = true
                    }
                    lcs[expectedIndex + 1][inputIndex] >= lcs[expectedIndex][inputIndex + 1] -> expectedIndex++
                    else -> inputIndex++
                }
            }
            return TypingDifference(expected, input, expectedCorrect, inputCorrect)
        }
    }
}
