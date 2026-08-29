package org.beobma.classWarPlugin.description

/** 상세 설명에서 플레이에 필요한 핵심 문장만 골라 기본 간략 설명을 만든다. */
object DescriptionText {
    private const val MAX_BRIEF_LINES = 4
    private const val MAX_OPTION_LINES = 6
    private val miniMessageTag = "<[^>]+>".toRegex()
    private val typeLabels = setOf("패시브", "스킬", "액티브", "무기")

    fun brief(detailed: List<String>): List<String> {
        val content = detailed
            .filterNot(::isManualCooldownLabel)
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { plain(it) in typeLabels }

        if (content.isEmpty()) {
            return listOf("<gray>별도의 추가 효과가 없습니다.")
        }
        if (content.size <= MAX_BRIEF_LINES) return content

        val optionLines = content.filter(::isOptionLine)
        if (optionLines.size >= 2) {
            val firstOptionIndex = content.indexOfFirst(::isOptionLine)
            val introduction = content.take(firstOptionIndex)
                .filterNot { plain(it).endsWith(":") }
                .take(2)
            return (introduction + optionLines.take(MAX_OPTION_LINES)).distinct()
        }

        val selectedIndices = linkedSetOf<Int>()
        content.indices.take(2).forEach(selectedIndices::add)
        content.indices.drop(2).filter { isImportantLine(content[it]) }.forEach { index ->
            if (selectedIndices.size < MAX_BRIEF_LINES) selectedIndices += index
        }
        content.indices.drop(2).forEach { index ->
            if (selectedIndices.size < MAX_BRIEF_LINES) selectedIndices += index
        }
        return selectedIndices.sorted().map(content::get)
    }

    fun plain(line: String): String = line.replace(miniMessageTag, "").trim()

    fun isTypeLabel(line: String): Boolean = plain(line) in typeLabels

    fun isOptionLine(line: String): Boolean {
        val text = plain(line)
        return text.startsWith("-") || text.startsWith("•") || text.startsWith("└")
    }

    private fun isImportantLine(line: String): Boolean {
        val text = plain(line)
        return importantTerms.any(text::contains)
    }

    private fun isManualCooldownLabel(line: String): Boolean {
        val text = plain(line)
        return text.startsWith("재사용 대기 시간") || text.startsWith("재사용 대기시간") ||
            text.startsWith("쿨타임")
    }

    private val importantTerms = listOf(
        "피해", "회복", "처형", "사망", "이동", "속도", "기본 공격", "적중", "지속", "최대",
        "감소", "증가", "무효", "사용할 수", "웅크", "상태", "대상", "범위", "초간",
    )
}
