package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.format.TextDecoration
import org.beobma.classWarPlugin.keyword.Keyword
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
            lore(render(sanitize(details), alwaysVisibleLines))
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
    ) = buildList {
        addAll(lines.map(::renderLoreLine))
        val keywordExplanations = Keyword.explanationsFor(lines).filterNot(lines::contains)
        if (keywordExplanations.isNotEmpty()) {
            if (isNotEmpty() && lines.lastOrNull()?.isNotBlank() == true) {
                add(renderLoreLine(""))
            }
            addAll(keywordExplanations.map(::renderLoreLine))
        }
        if (alwaysVisibleLines.isNotEmpty()) {
            if (isNotEmpty()) add(renderLoreLine(""))
            addAll(alwaysVisibleLines.map(::renderLoreLine))
        }
    }

    private fun sanitize(lines: List<String>): List<String> = lines
        .filterNot(::isManualCooldownLabel)
        .dropLastWhile(String::isBlank)

    private fun isManualCooldownLabel(line: String): Boolean =
        cooldownLabel.containsMatchIn(line.replace(miniMessageTag, "").trim())
}
