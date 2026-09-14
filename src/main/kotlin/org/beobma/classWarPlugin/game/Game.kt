package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.ability.GameClock

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.GameClass
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * 한 경기의 참가자, 설정, 진행 단계와 런타임 자원을 소유하는 집합 루트다.
 *
 * [settings]는 경기 생성 시점의 스냅샷이므로 이후 서버 설정 변경에 영향을 받지 않는다.
 * [tasks]와 표시 엔티티 등의 자원은 경기 종료 시 매니저가 일괄 정리한다.
 */
class Game(
    val playerDatas: MutableList<EntityData>,
    val settings: GameConfiguration = GameSettings.snapshot(),
    val mode: MatchMode = MatchMode.CLASSIC,
    var phase: GamePhase = GamePhase.WAITING,
    val tasks: MutableList<BukkitTask> = mutableListOf(),
    val availableClasses: MutableList<GameClass> = mutableListOf(),
    val refreshesRemaining: MutableMap<UUID, Int> = mutableMapOf(),
    val confirmedPlayers: MutableSet<UUID> = mutableSetOf(),
    val playerKillCounts: MutableMap<UUID, Int> = mutableMapOf(),
    val livesRemaining: MutableMap<UUID, Int> = mutableMapOf(),
    val spawnLocations: MutableList<Location> = mutableListOf(),
    val assignedSpawnLocations: MutableMap<UUID, Location> = mutableMapOf(),
    val disconnectedPlayers: MutableSet<UUID> = mutableSetOf(),
    val expiredReconnectPlayers: MutableSet<UUID> = mutableSetOf(),
    val disconnectTasks: MutableMap<UUID, BukkitTask> = mutableMapOf(),
    val battleInitializedPlayers: MutableSet<UUID> = mutableSetOf(),
    val tailTargets: MutableMap<UUID, UUID> = mutableMapOf(),
    /** 모든 참가자의 승패·아군 판정 단위. 개인전도 각자 서로 다른 팀 번호를 가진다. */
    val combatTeams: MutableMap<UUID, Int> = mutableMapOf(),
    /** 공동 모드에서 조작을 공유하는 더 작은 조 단위다. */
    val cooperativeGroups: MutableMap<UUID, Int> = mutableMapOf(),
    val cooperativeRoles: MutableMap<UUID, CooperativeRole> = mutableMapOf(),
    /** 꼬리잡기에서 각 전투 팀이 추적할 상대 팀이다. */
    val tailTargetTeams: MutableMap<Int, Int> = mutableMapOf(),
    val playerSnapshots: MutableMap<UUID, PlayerSnapshot> = mutableMapOf(),
    var borderBossBar: BossBar? = null,
    var originalBorderCenter: Location? = null,
    var originalBorderSize: Double? = null,
    var originalWorldTime: Long? = null,
    var originalDaylightCycle: Boolean? = null,
    var originalLocatorBar: Boolean? = null,
    val tickSource: () -> Long = { org.bukkit.Bukkit.getCurrentTick().toLong() },
    val finalBorderDisplays: MutableList<BlockDisplay> = mutableListOf(),
) {
    private val combatClock = GameClock(tickSource)
    val combatTick: Long get() = combatClock.now()
    var isPaused: Boolean
        get() = combatClock.paused
        set(value) { combatClock.paused = value }
    var roundCenterX: Double = settings.centerX
    var roundCenterZ: Double = settings.centerZ
    var battleMapView: MapView? = null
    var battleMapRenderer: MapRenderer? = null
    var finalBorderCompleted: Boolean = false

    /** 꼬리잡기 모드에서 [playerId]가 공격해야 하는 표적을 반환한다. */
    fun targetOf(playerId: UUID): UUID? = tailTargets[playerId]

    /** 꼬리잡기 모드에서 [playerId]를 표적으로 삼는 참가자를 반환한다. */
    fun threatOf(playerId: UUID): UUID? =
        tailTargets.entries.firstOrNull { (_, targetId) -> targetId == playerId }?.key

    fun teamOf(playerId: UUID): Int? = combatTeams[playerId]

    fun areAllies(firstId: UUID, secondId: UUID): Boolean {
        if (firstId == secondId) return true
        val firstTeam = combatTeams[firstId] ?: return false
        return firstTeam == combatTeams[secondId]
    }

    fun cooperativeRoleOf(playerId: UUID): CooperativeRole? = cooperativeRoles[playerId]

    fun canPerform(playerId: UUID, action: CooperativeAction): Boolean {
        if (!mode.usesCooperativeRules || phase != GamePhase.RUNNING) return true
        val role = cooperativeRoles[playerId] ?: return false
        return when (action) {
            CooperativeAction.MOVE -> role.canMove
            CooperativeAction.BASIC_ATTACK -> role.canBasicAttack
            CooperativeAction.CHANGE_HOTBAR -> role.canChangeHotbar
            CooperativeAction.USE_SKILL -> role.canUseSkills
        }
    }

    /**
     * 현재 모드 규칙에 따라 두 참가자의 적대 관계를 판정한다.
     * 일반 모드에서는 자기 자신을 제외한 모두, 꼬리잡기에서는 현재 지정 표적만 적이다.
     */
    fun areEnemies(attackerId: UUID, targetId: UUID): Boolean {
        if (attackerId == targetId || areAllies(attackerId, targetId)) return false
        if (!mode.usesTailTagRules) return true
        val attackerTeam = teamOf(attackerId) ?: return false
        return tailTargetTeams[attackerTeam] == teamOf(targetId)
    }
}

enum class CooperativeAction {
    MOVE,
    BASIC_ATTACK,
    CHANGE_HOTBAR,
    USE_SKILL,
}
