package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.format.TextDecoration
import org.beobma.classWarPlugin.description.DescriptionText
import org.beobma.classWarPlugin.keyword.Keyword
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** 아이템에 전체 설명과 항상 표시할 부가 정보를 적용한다. */
object ItemDescriptionManager {
    private val miniMessageTag = "<[^>]+>".toRegex()
    private val cooldownLabel = "^(재사용\\s*대기\\s*시간|쿨타임)\\s*[:：]".toRegex()
    private val miniMessage = MiniMessage.miniMessage()

    fun apply(
        item: ItemStack,
        details: List<String>,
        alwaysVisibleLines: List<String> = emptyList(),
    ): ItemStack = item.apply {
        if (type.isAir) return@apply
        itemMeta = itemMeta.apply {
            lore(render(sanitize(details), alwaysVisibleLines, DescriptionViewMode.DETAILED))
        }
    }

    fun applyForPlayer(
        item: ItemStack,
        player: Player,
        detailedDescription: List<String>,
        briefDescription: List<String> = DescriptionText.brief(detailedDescription),
        alwaysVisibleLines: List<String> = emptyList(),
    ): ItemStack = item.apply {
        if (type.isAir) return@apply
        val mode = PlayerPreferenceManager.descriptionViewMode(player)
        val selected = if (mode == DescriptionViewMode.DETAILED) detailedDescription else briefDescription
        itemMeta = itemMeta.apply {
            lore(render(sanitize(selected), alwaysVisibleLines, mode, detailedDescription))
        }
    }

    fun cooldownLines(cooldown: Int?): List<String> = listOf(
        when (cooldown) {
            null, 0 -> "<dark_gray>재사용 대기시간: <gray>없음"
            Int.MAX_VALUE -> "<dark_gray>재사용 대기시간: <gray>재사용 불가"
            else -> "<dark_gray>재사용 대기시간: <gray>${cooldown.coerceAtLeast(0)}초"
        }
    )

    fun renderLoreLine(line: String) =
        miniMessage.deserialize(UtilManager.applyKeywords(line))
            .decoration(TextDecoration.ITALIC, false)

    private fun render(
        lines: List<String>,
        alwaysVisibleLines: List<String>,
        mode: DescriptionViewMode,
        keywordSource: List<String> = lines,
    ) = buildList {
        add(renderLoreLine(
            if (mode == DescriptionViewMode.DETAILED) "<gold><bold>상세 효과</bold>"
            else "<green><bold>핵심 효과</bold>"
        ))
        add(renderLoreLine("<dark_gray>────────────"))
        addAll(formatEffectLines(lines).map(::renderLoreLine))

        val keywordExplanations = when (mode) {
            DescriptionViewMode.DETAILED -> Keyword.explanationsFor(keywordSource)
            DescriptionViewMode.BRIEF -> Keyword.briefExplanationsFor(keywordSource)
        }.filterNot(lines::contains)
        if (keywordExplanations.isNotEmpty()) {
            if (isNotEmpty()) add(renderLoreLine(""))
            add(renderLoreLine(
                if (mode == DescriptionViewMode.DETAILED) "<aqua><bold>용어 설명</bold>"
                else "<aqua><bold>필수 용어</bold>"
            ))
            addAll(keywordExplanations.map { renderLoreLine("<dark_gray>• </dark_gray>$it") })
        }
        if (alwaysVisibleLines.isNotEmpty()) {
            if (isNotEmpty()) add(renderLoreLine(""))
            add(renderLoreLine("<yellow><bold>사용 정보</bold>"))
            addAll(alwaysVisibleLines.map(::renderLoreLine))
        }
    }

    private fun formatEffectLines(lines: List<String>): List<String> {
        val content = lines.filterNot(DescriptionText::isTypeLabel)
        if (content.none(String::isNotBlank)) {
            return listOf("<dark_gray>• </dark_gray><gray>별도의 추가 효과가 없습니다.")
        }

        val result = mutableListOf<String>()
        content.forEach { line ->
            if (line.isBlank()) {
                if (result.lastOrNull()?.isNotBlank() == true) result += ""
                return@forEach
            }
            result += if (DescriptionText.isOptionLine(line)) {
                "<dark_gray>  └ </dark_gray>${line.replaceFirst("-", "").trim()}"
            } else {
                "<dark_gray>• </dark_gray>$line"
            }
        }
        return result.dropLastWhile(String::isBlank)
    }

    private fun sanitize(lines: List<String>): List<String> = lines
        .filterNot(::isManualCooldownLabel)
        .dropLastWhile(String::isBlank)

    private fun isManualCooldownLabel(line: String): Boolean =
        cooldownLabel.containsMatchIn(line.replace(miniMessageTag, "").trim())
}
