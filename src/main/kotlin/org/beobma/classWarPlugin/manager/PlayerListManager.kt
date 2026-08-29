package org.beobma.classWarPlugin.manager

import org.bukkit.Bukkit
import java.util.UUID

/** 경기 중 탭 목록에서 플레이어를 숨기고 기존 노출 상태만 복원한다. */
object PlayerListManager {
    private data class HiddenEntry(
        val viewerId: UUID,
        val targetId: UUID,
    )

    private val hiddenEntries: MutableSet<HiddenEntry> = mutableSetOf()

    /** 현재 온라인 플레이어 조합 중 실제로 숨긴 항목을 기록한다. */
    fun hideAll() {
        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        onlinePlayers.forEach { viewer ->
            onlinePlayers.forEach { target ->
                if (viewer.isListed(target) && viewer.unlistPlayer(target)) {
                    hiddenEntries.add(HiddenEntry(viewer.uniqueId, target.uniqueId))
                }
            }
        }
    }

    /** 이 매니저가 숨겼으며 현재 서로 볼 수 있는 항목만 탭 목록에 복원한다. */
    fun restoreAll() {
        hiddenEntries.forEach { entry ->
            val viewer = Bukkit.getPlayer(entry.viewerId)?.takeIf { it.isOnline } ?: return@forEach
            val target = Bukkit.getPlayer(entry.targetId)?.takeIf { it.isOnline } ?: return@forEach
            if (viewer.canSee(target)) viewer.listPlayer(target)
        }
        hiddenEntries.clear()
    }
}
