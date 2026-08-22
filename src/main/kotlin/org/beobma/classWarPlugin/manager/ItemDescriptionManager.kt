package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.keyword.Keyword
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** 아이템의 간략/상세 설명을 아이템 자체에 안전하게 보관하고 전환한다. */
object ItemDescriptionManager {
    private const val lineSeparator = "\u001F"
    private val miniMessageTag = "<[^>]+>".toRegex()
    private val cooldownLabel = "^(재사용\\s*대기\\s*시간|쿨타임)\\s*[:：]".toRegex()
    private val miniMessage = MiniMessage.miniMessage()
    private val summaryKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "description-summary")
    private val detailsKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "description-details")
    private val expandedKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "description-expanded")
    private val alwaysVisibleKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "description-always-visible")

    fun apply(
        item: ItemStack,
        summary: List<String>,
        details: List<String>,
        alwaysVisibleLines: List<String> = emptyList(),
        showToggleHint: Boolean = true,
    ): ItemStack = item.apply {
        if (type.isAir) return@apply
        itemMeta = itemMeta.apply {
            val sanitizedDetails = sanitize(details)
            val effectiveSummary = sanitize(summary)
                .filterNot(Keyword::isExplanation)
                .ifEmpty {
                    sanitizedDetails.filter { it.isNotBlank() && !Keyword.isExplanation(it) }.take(2)
                }
            persistentDataContainer.set(summaryKey, PersistentDataType.STRING, encode(effectiveSummary))
            persistentDataContainer.set(detailsKey, PersistentDataType.STRING, encode(sanitizedDetails))
            persistentDataContainer.set(alwaysVisibleKey, PersistentDataType.STRING, encode(alwaysVisibleLines))
            persistentDataContainer.set(expandedKey, PersistentDataType.BYTE, 0)
            lore(render(effectiveSummary, alwaysVisibleLines, expanded = false, showToggleHint))
        }
    }

    fun toggle(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val meta = item.itemMeta ?: return false
        val container = meta.persistentDataContainer
        val summary = container.get(summaryKey, PersistentDataType.STRING)?.let(::decode) ?: return false
        val details = container.get(detailsKey, PersistentDataType.STRING)?.let(::decode) ?: return false
        val alwaysVisibleLines = container.get(alwaysVisibleKey, PersistentDataType.STRING)?.let(::decode).orEmpty()
        val expanded = container.get(expandedKey, PersistentDataType.BYTE)?.toInt() == 1
        val nextExpanded = !expanded

        container.set(expandedKey, PersistentDataType.BYTE, if (nextExpanded) 1 else 0)
        meta.lore(render(
            if (nextExpanded) details else summary,
            alwaysVisibleLines,
            nextExpanded,
            showToggleHint = true,
        ))
        item.itemMeta = meta
        return true
    }

    fun cooldownLines(cooldown: Int?): List<String> = listOf(
        when (cooldown) {
            null, 0 -> "<dark_gray>재사용 대기시간: <gray>없음"
            Int.MAX_VALUE -> "<dark_gray>재사용 대기시간: <gray>재사용 불가"
            else -> "<dark_gray>재사용 대기시간: <gray>${cooldown.coerceAtLeast(0)}초"
        }
    )

    private fun render(
        lines: List<String>,
        alwaysVisibleLines: List<String>,
        expanded: Boolean,
        showToggleHint: Boolean,
    ) = buildList {
        addAll(lines.map { miniMessage.deserialize(UtilManager.applyKeywords(it)) })
        if (showToggleHint) {
            if (lines.isNotEmpty()) add(miniMessage.deserialize(""))
            add(miniMessage.deserialize(
                if (expanded) {
                    "<dark_gray>Shift + 우클릭: 간략히 보기"
                } else {
                    "<dark_gray>Shift + 우클릭: 상세 설명 보기"
                }
            ))
        }
        if (alwaysVisibleLines.isNotEmpty()) {
            if (isNotEmpty()) add(miniMessage.deserialize(""))
            addAll(alwaysVisibleLines.map { miniMessage.deserialize(UtilManager.applyKeywords(it)) })
        }
    }

    private fun sanitize(lines: List<String>): List<String> = lines
        .filterNot(::isManualCooldownLabel)
        .dropLastWhile(String::isBlank)

    private fun isManualCooldownLabel(line: String): Boolean =
        cooldownLabel.containsMatchIn(line.replace(miniMessageTag, "").trim())

    private fun encode(lines: List<String>): String = lines.joinToString(lineSeparator)

    private fun decode(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(lineSeparator)
}
