package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.beobma.classWarPlugin.gameClass.list.PlanetPowerRegistry
import org.beobma.classWarPlugin.gameClass.list.Terra
import java.util.UUID

/** 최근 교전 시각을 추적해 자연 회복 차단 여부를 판정한다. */
object CombatManager {
    private const val COMBAT_TIMEOUT_TICKS = 20L * 20L
    private val lastCombatTickByPlayer: MutableMap<UUID, Long> = mutableMapOf()

    /** 피해를 준 참가자와 플레이어 피격자 모두를 교전 상태로 표시한다. */
    fun recordSuccessfulDamage(context: DamageContext) {
        mark(context.attacker.uniqueId)
        (context.target as? PlayerData)?.let { mark(it.uniqueId) }
    }

    /** 바닐라 피해 등 별도 경로로 피해를 받은 참가자를 교전 상태로 표시한다. */
    fun recordDamageTaken(playerData: PlayerData) {
        mark(playerData.uniqueId)
    }

    /**
     * 마지막 교전 이후 자연 회복 차단 시간이 남았는지 반환한다.
     * 기본 제한은 20초이며 특정 클래스 효과는 5초로 단축한다.
     */
    fun blocksNaturalRegeneration(player: Player): Boolean {
        val lastCombatTick = lastCombatTickByPlayer[player.uniqueId] ?: return false
        val playerData = GameManager.findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.find { it.uniqueId == player.uniqueId }
        val timeout = if (playerData != null && PlanetPowerRegistry.hasPower(playerData, Terra::class.java)) {
            5L * 20L
        } else {
            COMBAT_TIMEOUT_TICKS
        }
        if (currentTick() - lastCombatTick < timeout) return true
        lastCombatTickByPlayer.remove(player.uniqueId)
        return false
    }

    /** 지정한 플레이어들의 교전 기록을 제거한다. */
    fun clear(playerIds: Collection<UUID>) {
        playerIds.forEach(lastCombatTickByPlayer::remove)
    }

    private fun mark(playerId: UUID) {
        lastCombatTickByPlayer[playerId] = currentTick()
    }

    private fun currentTick(): Long = Bukkit.getCurrentTick().toLong()
}
