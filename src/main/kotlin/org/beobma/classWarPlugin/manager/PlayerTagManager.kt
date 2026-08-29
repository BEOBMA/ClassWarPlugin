package org.beobma.classWarPlugin.manager

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 불리언 플레이어 태그의 직렬화 이름을 타입 안전하게 제공한다. */
enum class PlayerFlag(internal val serializedName: String) {
    TRAINING("isTraining"),
    OPEN_GAME_MODE_INVENTORY("openGameModeInventory"),
    OPEN_ASSIGNED_CLASS_INVENTORY("openAssignedClassInventory"),
    OPENING_ASSIGNED_CLASS_INVENTORY("openingAssignedClassInventory"),
    OPEN_CONFIG_INVENTORY("openConfigInventory"),
    OPENING_CONFIG_INVENTORY("openingConfigInventory"),
    OPEN_CLASS_LIST_INVENTORY("openClassListInventory"),
    OPEN_TRAINING_CLASS_LIST_INVENTORY("openTrainingClassListInventory"),
    OPEN_CLASS_STATUS_INVENTORY("openClassStatusInventory"),
    OPENING_CLASS_STATUS_INVENTORY("openingClassStatusInventory"),
}

/** 접두사 뒤에 하나의 값을 저장하는 플레이어 태그 키다. */
enum class PlayerTagValue(internal val prefix: String) {
    CONFIG_CATEGORY("configCategory:"),
    CLASS_LIST_PAGE("classListPage:"),
    CLASS_STATUS_RETURN("classStatusReturn:"),
    CLASS_BALANCE_PAGE("classBalancePage:"),
    CLASS_BALANCE_CLASS("classBalanceClass:"),
}

/**
 * UI와 훈련 상태에 쓰는 세션 한정 플레이어 태그 저장소다.
 * 값은 메모리에만 존재하며 [clear]하거나 서버가 종료되면 사라진다.
 */
object PlayerTagManager {
    private val tagsByPlayer = ConcurrentHashMap<UUID, MutableSet<String>>()

    private fun tags(player: Player): MutableSet<String> =
        tagsByPlayer.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }

    /** 태그를 조회한다. 조회만으로 플레이어 저장 공간을 만들지 않는다. */
    fun hasTag(player: Player, tag: String): Boolean =
        tagsByPlayer[player.uniqueId]?.contains(tag) == true

    fun hasFlag(player: Player, flag: PlayerFlag): Boolean = hasTag(player, flag.serializedName)

    fun isTraining(player: Player): Boolean = hasFlag(player, PlayerFlag.TRAINING)

    fun addTag(player: Player, tag: String) {
        tags(player).add(tag)
    }

    fun addFlag(player: Player, flag: PlayerFlag) {
        addTag(player, flag.serializedName)
    }

    fun removeTag(player: Player, tag: String) {
        mutateExisting(player) { it.remove(tag) }
    }

    fun removeFlag(player: Player, flag: PlayerFlag) {
        removeTag(player, flag.serializedName)
    }

    fun removeIf(player: Player, predicate: (String) -> Boolean) {
        mutateExisting(player) { it.removeIf(predicate) }
    }

    fun findTag(player: Player, predicate: (String) -> Boolean): String? =
        tagsByPlayer[player.uniqueId]?.firstOrNull(predicate)

    /** 같은 [key]의 기존 값을 제거하고 하나의 새 직렬화 값을 저장한다. */
    fun setValue(player: Player, key: PlayerTagValue, value: Any) {
        val serialized = key.prefix + value
        tagsByPlayer.compute(player.uniqueId) { _, existing ->
            val updated = existing ?: ConcurrentHashMap.newKeySet()
            updated.removeIf { it.startsWith(key.prefix) }
            updated.add(serialized)
            updated
        }
    }

    /** [key]에 저장된 접두사 없는 값을 반환한다. */
    fun getValue(player: Player, key: PlayerTagValue): String? =
        findTag(player) { it.startsWith(key.prefix) }?.removePrefix(key.prefix)

    fun removeValue(player: Player, key: PlayerTagValue) {
        removeIf(player) { it.startsWith(key.prefix) }
    }

    /** [player]의 모든 세션 태그를 제거한다. */
    fun clear(player: Player) {
        tagsByPlayer.remove(player.uniqueId)
    }

    private fun mutateExisting(player: Player, mutation: (MutableSet<String>) -> Unit) {
        tagsByPlayer.computeIfPresent(player.uniqueId) { _, existing ->
            mutation(existing)
            existing.takeUnless { it.isEmpty() }
        }
    }
}
