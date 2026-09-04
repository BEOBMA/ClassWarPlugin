package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.ceil

/**
 * 플레이어·스킬 조합별 재사용 대기시간을 경기의 전투 시계로 추적한다.
 * Bukkit 아이템 쿨다운은 같은 재료를 쓰는 활성 스킬 중 가장 긴 남은 시간으로 동기화된다.
 */
object CooldownManager {

    private data class CooldownKey(val playerId: UUID, val skillId: String)
    private data class CooldownEntry(
        val expiresAtTick: Long,
        val material: Material,
        val clock: () -> Long,
        val pausedAtTick: Long? = null,
    )

    private val cooldowns: MutableMap<CooldownKey, CooldownEntry> = mutableMapOf()

    /** [skill]의 재사용 대기시간이 한 틱 이상 남아 있는지 반환한다. */
    fun hasCooldown(player: Player, skill: Skill): Boolean = remainingTicks(player, skill) > 0

    /** 남은 시간을 틱 단위로 올림해 반환하며 만료된 항목은 제거한다. */
    fun remainingTicks(player: Player, skill: Skill): Int {
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return 0
        val remaining = remainingTicks(entry)
        if (remaining <= 0) cooldowns.remove(key)
        return remaining
    }

    /**
     * [ticks]를 기본값으로 새 재사용 대기시간을 설정한다.
     * 클래스와 경기의 쿨다운 흐름 배율은 설정 시점에 반영된다. `0` 이하는 기존 값을 해제한다.
     */
    fun setCooldown(player: Player, skill: Skill, item: ItemStack, ticks: Int) {
        val key = CooldownKey(player.uniqueId, skill.id)
        if (ticks <= 0) {
            val material = cooldowns.remove(key)?.material ?: item.type
            refreshMaterialCooldown(player, material)
            return
        }
        val flowMultiplier = ClassBalanceManager.cooldownFlowMultiplier(player, skill)
        val effectiveTicks = effectiveCooldownTicks(ticks, flowMultiplier)
        val clock = { skill.abilityScope.game.combatTick }
        cooldowns[key] = CooldownEntry(clock() + effectiveTicks, item.type, clock)
        refreshMaterialCooldown(player, item.type)
    }

    /** [skill]의 재사용 대기시간을 즉시 해제한다. */
    fun resetCooldown(player: Player, skill: Skill) {
        val key = CooldownKey(player.uniqueId, skill.id)
        val material = cooldowns.remove(key)?.material ?: return
        refreshMaterialCooldown(player, material)
    }

    /** 현재 만료 시각을 [ticks]만큼 앞당긴다. */
    fun reduceCooldown(player: Player, skill: Skill, ticks: Int) {
        if (ticks <= 0) return
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        cooldowns[key] = entry.copy(expiresAtTick = entry.expiresAtTick - ticks)
        refreshMaterialCooldown(player, entry.material)
    }

    /** 현재 남은 시간에 [multiplier]를 곱한다. 양수가 아닌 배율은 무시한다. */
    fun multiplyCooldown(player: Player, skill: Skill, multiplier: Double) {
        if (multiplier <= 0.0) return
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        val now = entry.pausedAtTick ?: entry.clock()
        val remainingTicks = (entry.expiresAtTick - now).coerceAtLeast(0L)
        val multiplied = (remainingTicks.toDouble() * multiplier).toLong()
        cooldowns[key] = entry.copy(expiresAtTick = now + multiplied)
        refreshMaterialCooldown(player, entry.material)
    }

    /** 현재 남은 시간을 유지한 채 흐름을 정지한다. 이미 정지된 항목에는 영향이 없다. */
    fun pauseCooldown(player: Player, skill: Skill) {
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        if (entry.pausedAtTick != null) return
        cooldowns[key] = entry.copy(pausedAtTick = entry.clock())
        refreshMaterialCooldown(player, entry.material)
    }

    /** 정지 중 경과한 전투 시간을 만료 시각에 더해 흐름을 재개한다. */
    fun resumeCooldown(player: Player, skill: Skill) {
        val key = CooldownKey(player.uniqueId, skill.id)
        val entry = cooldowns[key] ?: return
        val pausedAt = entry.pausedAtTick ?: return
        cooldowns[key] = entry.copy(
            expiresAtTick = entry.expiresAtTick + (entry.clock() - pausedAt),
            pausedAtTick = null,
        )
        refreshMaterialCooldown(player, entry.material)
    }

    /** 지정한 플레이어들의 내부 재사용 대기시간 상태를 제거한다. */
    fun clear(playerIds: Collection<UUID>) {
        if (playerIds.isEmpty()) return
        cooldowns.keys.removeIf { it.playerId in playerIds }
    }

    /** 내부 상태를 기준으로 [player]의 모든 관련 아이템 쿨다운 표시를 다시 전송한다. */
    fun refreshPlayer(player: Player) {
        cooldowns.asSequence()
            .filter { (key, _) -> key.playerId == player.uniqueId }
            .map { (_, entry) -> entry.material }
            .distinct()
            .forEach { material -> refreshMaterialCooldown(player, material) }
    }

    private fun refreshMaterialCooldown(player: Player, material: Material) {
        val maximumRemaining = cooldowns.asSequence()
            .filter { (key, entry) -> key.playerId == player.uniqueId && entry.material == material }
            .maxOfOrNull { (_, entry) -> remainingTicks(entry) }
            ?.coerceAtLeast(0)
            ?: 0
        player.setCooldown(material, maximumRemaining)
    }

    private fun remainingTicks(entry: CooldownEntry): Int {
        val now = entry.pausedAtTick ?: entry.clock()
        return (entry.expiresAtTick - now).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    internal fun effectiveCooldownTicks(ticks: Int, flowMultiplier: Double): Long {
        if (ticks <= 0) return 0L
        val safeMultiplier = flowMultiplier.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        return ceil(ticks.toDouble() / safeMultiplier).toLong().coerceAtLeast(1L)
    }
}
