package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

/** 클래스 아이템 설명의 간략/상세 표시 방식이다. */
enum class DescriptionViewMode {
    BRIEF,
    DETAILED,
}

/** 서버 공용 설정과 분리된 플레이어별 표시 설정이다. */
object PlayerPreferenceManager {
    private val detailedDescriptionKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "detailed-class-descriptions")

    /** 플레이어 영속 데이터에 저장된 설명 표시 방식을 반환한다. */
    fun descriptionViewMode(player: Player): DescriptionViewMode =
        if (player.persistentDataContainer.get(detailedDescriptionKey, PersistentDataType.BYTE)?.toInt() == 1) {
            DescriptionViewMode.DETAILED
        } else {
            DescriptionViewMode.BRIEF
        }

    fun usesDetailedDescriptions(player: Player): Boolean =
        descriptionViewMode(player) == DescriptionViewMode.DETAILED

    /** 설명 표시 방식을 전환해 저장하고 새 방식을 반환한다. */
    fun toggleDescriptionViewMode(player: Player): DescriptionViewMode {
        val next = if (usesDetailedDescriptions(player)) DescriptionViewMode.BRIEF else DescriptionViewMode.DETAILED
        player.persistentDataContainer.set(
            detailedDescriptionKey,
            PersistentDataType.BYTE,
            if (next == DescriptionViewMode.DETAILED) 1 else 0,
        )
        return next
    }
}
