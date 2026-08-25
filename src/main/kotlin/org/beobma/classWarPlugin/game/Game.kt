package org.beobma.classWarPlugin.game

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.GameClass
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Location
import org.bukkit.entity.BlockDisplay
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

data class Game(
    val playerDatas: MutableList<EntityData>,
    val settings: GameConfiguration = GameSettings.snapshot(),
    val mode: MatchMode = MatchMode.CLASSIC,
    var phase: GamePhase = GamePhase.WAITING,
    val tasks: MutableList<BukkitTask> = mutableListOf(),
    val availableClasses: MutableList<GameClass> = mutableListOf(),
    val refreshesRemaining: MutableMap<UUID, Int> = mutableMapOf(),
    val confirmedPlayers: MutableSet<UUID> = mutableSetOf(),
    val playerKillCounts: MutableMap<UUID, Int> = mutableMapOf(),
    val spawnLocations: MutableList<Location> = mutableListOf(),
    val assignedSpawnLocations: MutableMap<UUID, Location> = mutableMapOf(),
    val disconnectedPlayers: MutableSet<UUID> = mutableSetOf(),
    val expiredReconnectPlayers: MutableSet<UUID> = mutableSetOf(),
    val disconnectTasks: MutableMap<UUID, BukkitTask> = mutableMapOf(),
    val battleInitializedPlayers: MutableSet<UUID> = mutableSetOf(),
    val tailTargets: MutableMap<UUID, UUID> = mutableMapOf(),
    val playerSnapshots: MutableMap<UUID, PlayerSnapshot> = mutableMapOf(),
    var borderBossBar: BossBar? = null,
    var originalBorderCenter: Location? = null,
    var originalBorderSize: Double? = null,
    var originalWorldTime: Long? = null,
    var originalDaylightCycle: Boolean? = null,
    val finalBorderDisplays: MutableList<BlockDisplay> = mutableListOf(),
) {
    var isPaused: Boolean = false
    var roundCenterX: Double = settings.centerX
    var roundCenterZ: Double = settings.centerZ
    var battleMapView: MapView? = null
    var battleMapRenderer: MapRenderer? = null

    fun targetOf(playerId: UUID): UUID? = tailTargets[playerId]

    fun threatOf(playerId: UUID): UUID? =
        tailTargets.entries.firstOrNull { (_, targetId) -> targetId == playerId }?.key

    fun areEnemies(attackerId: UUID, targetId: UUID): Boolean = when (mode) {
        MatchMode.CLASSIC, MatchMode.DUAL -> attackerId != targetId
        MatchMode.TAIL_TAG -> tailTargets[attackerId] == targetId
    }
}
