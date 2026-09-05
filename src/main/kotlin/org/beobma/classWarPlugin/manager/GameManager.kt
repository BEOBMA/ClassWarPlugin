package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ability.AbilityCatalog
import org.beobma.classWarPlugin.ability.Targeting

import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.ability.EndReason

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.DamageMultiplierType
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.game.MatchMode
import org.beobma.classWarPlugin.game.PlayerSnapshot
import org.beobma.classWarPlugin.game.damageMultiplier
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.gameClass.list.*
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.InventoryManager.openAssignedClassInventory
import org.beobma.classWarPlugin.manager.PlayerManager.classSet
import org.beobma.classWarPlugin.manager.PlayerManager.clearDamageInvincibility
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.unregisterAllTickingStatuses
import org.beobma.classWarPlugin.manager.UtilManager.getPlayerMaxHealth
import org.beobma.classWarPlugin.manager.UtilManager.resetDyeCooldowns
import org.beobma.classWarPlugin.util.PlayerNavigation
import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.CompletableFuture
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 정규 경기와 훈련 경기의 생성, 단계 전환, 참가자 복구 및 모든 런타임 자원 정리를 조율한다.
 * 정규 경기는 동시에 하나만 존재하며 Bukkit 월드의 첫 번째 월드를 전장으로 사용한다.
 */
object GameManager {
    /** 운영자 능력 변경 명령의 성공 여부와 사용자용 결과 메시지다. */
    data class AbilityChangeResult(val success: Boolean, val message: String)
    enum class DeathOutcome { BREAK, TERMINATE }

    private val miniMessage = MiniMessage.miniMessage()
    private const val RECONNECT_GRACE_TICKS = 5L * 60L * 20L
    private const val SPAWN_CENTER_TARGET_RADIUS = 4.0
    private const val SPAWN_PATH_MAX_VISITED_NODES = 6_000
    private const val SPAWN_SEARCH_ATTEMPTS = 64
    private const val SPAWN_BORDER_FALLBACK_ATTEMPTS = 96
    private const val SPAWN_CENTER_REPLACEMENT_ATTEMPTS = 96
    private const val SPAWN_SELECTION_TIME_BUDGET_NANOS = 300_000_000L
    private const val SPAWN_PATH_TIME_BUDGET_NANOS = 8_000_000L
    private const val SPAWN_DISTINCT_FALLBACK_ATTEMPTS_PER_PLAYER = 96
    private const val SPAWN_BORDER_MARGIN = 0.35
    private const val SPAWN_BORDER_FALLBACK_MAX_RADIUS = 1_024.0
    private const val SPAWN_RELAXED_MINIMUM_DISTANCE_SQUARED = 2.25
    private const val SPAWN_COLUMN_SEARCH_DEPTH = 48
    private const val ROUND_CENTER_SEARCH_ATTEMPTS = 128
    private const val ROUND_SPAWN_LAYOUT_ATTEMPTS = 3
    private const val ROUND_CENTER_LAND_CHECK_RADIUS = 12
    private const val ROUND_CENTER_LAND_CHECK_STEP = 6
    private const val ROUND_CENTER_MINIMUM_DRY_RATIO = 0.4
    private const val FINAL_BORDER_DISPLAY_TILE_SIZE = 4.0
    private const val FINAL_BORDER_MAX_TILES_PER_AXIS = 20
private const val FINAL_BORDER_UPDATE_INTERVAL_TICKS = 2L
private const val BORDER_BOSS_BAR_UPDATE_INTERVAL_TICKS = 10L
    private const val TAIL_HEARTBEAT_RADIUS = 48.0
    private const val TAIL_HEARTBEAT_MIN_INTERVAL_TICKS = 7
    private const val TAIL_HEARTBEAT_MAX_INTERVAL_TICKS = 36
    private val pendingPostGameCleanup: MutableMap<UUID, PlayerSnapshot> = mutableMapOf()

    private data class SpawnBoundary(
        val minX: Double,
        val maxX: Double,
        val minZ: Double,
        val maxZ: Double,
    ) {
        fun contains(x: Double, z: Double): Boolean =
            x in minX..maxX && z in minZ..maxZ

        fun contains(location: Location): Boolean = contains(location.x, location.z)
    }

    // 이 목록에 등록된 클래스만 실제 배정, 클래스 목록 및 훈련 선택에 노출된다.
    // 심판자, 숨바꼭질, 공포, 백룸은 비활성화 대상으로 의도적으로 등록하지 않는다.

    private val miniMessageTagPattern = Regex("<[^>]+>")

    /** 매 조회마다 새 클래스 인스턴스를 만들고 등급·표시 이름 순으로 정렬한 목록이다. */
    val gameClassList: List<GameClass>
        get() = AbilityCatalog.enabledClasses()
            .sortedWith(
                compareBy<GameClass> { it.rank.ordinal }
                    .thenBy { it.name.replace(miniMessageTagPattern, "") }
                    .thenBy { it.javaClass.simpleName }
            )

    val gameWorld: World
        get() = Bukkit.getWorlds().first()

    val trainingInstance: MutableList<Game> = mutableListOf()

    /**
     * 온라인 비훈련 플레이어로 [mode] 경기를 생성한다.
     *
     * @return 시작에 성공하면 `null`, 실패하면 사용자에게 표시할 사유
     */
    fun startNewGame(mode: MatchMode): String? {
        if (game != null) return "이미 진행중인 게임이 있습니다."

        val newGame = Game(mutableListOf(), mode = mode)
        val participants = Bukkit.getOnlinePlayers()
            .filterNot(PlayerTagManager::isTraining)
            .map { PlayerData(it, newGame) }
        if (participants.size <= 1) return "참가자가 2명 이상이여야 게임을 시작할 수 있습니다."
        val requiredClassCount = participants.size * mode.assignedClassCount
        if (requiredClassCount > availableClassesFor(mode).size) {
            return "사용 가능한 클래스 수보다 참가자가 많아 게임을 시작할 수 없습니다."
        }

        newGame.playerDatas.addAll(participants)
        newGame.start()
        return null
    }

    /** 참가자 상태를 보관하고 클래스 선택 단계로 경기를 시작한다. */
    fun Game.start() {
        val participants = activePlayers()
        if (participants.size * mode.assignedClassCount > availableClassesFor(mode).size) {
            sendNotification("사용 가능한 클래스 수보다 참가자가 많아 게임을 시작할 수 없습니다.")
            return
        }

        game = this
        phase = GamePhase.CLASS_SELECTION
        originalWorldTime = gameWorld.time
        originalDaylightCycle = gameWorld.getGameRuleValue(GameRules.ADVANCE_TIME)
        if (settings.playerListVisible) PlayerListManager.restoreAll() else PlayerListManager.hideAll()
        NameTagManager.hideAll(participants.map { it.player.name })
        availableClasses.clear()
        availableClasses.addAll(availableClassesFor(mode))
        confirmedPlayers.clear()
        refreshesRemaining.clear()
        playerKillCounts.clear()
        tailTargets.clear()

        participants.forEach { playerData ->
            val player = playerData.player
            playerSnapshots.putIfAbsent(player.uniqueId, PlayerSnapshot.capture(player))
            val status = playerData.entityStatus
            status.isDead = false
            status.canAttack = false
            status.canSkillUse = false
            status.canMove = true
            status.isAttackable = false
            status.isSkillTargeting = false
            player.gameMode = GameMode.ADVENTURE
            player.inventory.clear()
            player.fireTicks = 0
            player.health = player.getPlayerMaxHealth()
            refreshesRemaining[player.uniqueId] = settings.refreshChances
            val assignedClasses = mutableListOf<GameClass>()
            repeat(mode.assignedClassCount) {
                drawRandomClass()?.let(assignedClasses::add)
            }
            playerData.assignGameClasses(assignedClasses)
        }

        if (participants.any { it.gameClasses.size != mode.assignedClassCount }) {
            sendNotification("클래스 배정에 실패하여 게임을 종료합니다.")
            stop()
            return
        }

        sendNotification("${mode.displayName} <gray>모드가 선택되었습니다. 무작위 클래스를 확인하고 확정해 주세요.")
        participants.forEach { it.openAssignedClassInventory() }
    }

    /** 클래스 선택 단계에서 남은 기회를 사용해 현재 배정 전체를 다시 추첨한다. */
    fun PlayerData.refreshAssignedClass() {
        val currentGame = initGame
        if (currentGame.phase != GamePhase.CLASS_SELECTION) return
        if (currentGame.confirmedPlayers.contains(player.uniqueId)) return

        val remaining = currentGame.refreshesRemaining[player.uniqueId] ?: 0
        if (remaining <= 0) {
            player.sendMessage(miniMessage.deserialize("<red><bold>[!] 남은 새로고침 횟수가 없습니다."))
            return
        }

        val previousClasses = gameClasses.toList()
        if (previousClasses.isEmpty()) return
        currentGame.availableClasses.addAll(previousClasses)
        val excludedTypes = previousClasses.map { it.javaClass }.toSet()
        val replacements = mutableListOf<GameClass>()
        repeat(currentGame.mode.assignedClassCount) {
            currentGame.drawRandomClass(excludedTypes)?.let(replacements::add)
        }
        if (replacements.size != currentGame.mode.assignedClassCount) {
            currentGame.availableClasses.addAll(replacements)
            previousClasses.forEach(currentGame.availableClasses::remove)
            player.sendMessage(miniMessage.deserialize("<red><bold>[!] 새로 배정할 수 있는 클래스 조합이 없습니다."))
            return
        }

        assignGameClasses(replacements)
        currentGame.refreshesRemaining[player.uniqueId] = remaining - 1
        player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0F, 1.2F)
        openAssignedClassInventory()
    }

    /** 현재 클래스 배정을 확정하고 모든 생존자가 확정했으면 카운트다운을 시작한다. */
    fun PlayerData.confirmAssignedClass() {
        val currentGame = initGame
        if (currentGame.phase != GamePhase.CLASS_SELECTION) return
        if (gameClasses.size != currentGame.mode.assignedClassCount) return
        if (!currentGame.confirmedPlayers.add(player.uniqueId)) return

        PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)
        player.closeInventory()
        player.playerListName(miniMessage.deserialize(player.name))
        currentGame.sendNotification("${player.name}님이 클래스를 확정했습니다. (${currentGame.confirmedPlayers.size}/${currentGame.contenders().size})")

        if (currentGame.contenders().all { currentGame.confirmedPlayers.contains(it.player.uniqueId) }) {
            currentGame.beginCountdown()
        }
    }

    private fun Game.drawRandomClass(excludedTypes: Set<Class<out GameClass>> = emptySet()): GameClass? {
        val candidates = availableClasses.filter { it.javaClass !in excludedTypes }
        if (candidates.isEmpty()) return null

        val weightedRanks = candidates.map { it.rank }.distinct().mapNotNull { rank ->
            val weight = settings.rankWeights[rank] ?: 0
            if (weight > 0) rank to weight else null
        }
        val totalWeight = weightedRanks.sumOf { it.second }
        if (totalWeight <= 0) return null
        var roll = Random.nextInt(totalWeight)
        val selectedRank = weightedRanks.first { (_, weight) ->
            roll -= weight
            roll < 0
        }.first
        val selected = candidates.filter { it.rank == selectedRank }.random()
        availableClasses.remove(selected)
        return selected
    }

    private fun availableClassesFor(mode: MatchMode): List<GameClass> = AbilityCatalog.enabledClasses()
        .filterNot { !mode.allowsParasite && it is Parasite }

    private fun Game.beginCountdown() {
        phase = GamePhase.COUNTDOWN
        val participants = contenders()
        initializeTailTargets(participants)
        participants.forEach {
            PlayerTagManager.removeFlag(it.player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)
            it.player.closeInventory()
            it.entityStatus.canMove = false
        }

        var foundCenter = false
        var spawnPoints: List<Location> = emptyList()
        for (layoutAttempt in 0 until ROUND_SPAWN_LAYOUT_ATTEMPTS) {
            if (!selectRandomRoundCenter()) continue
            foundCenter = true
            val candidates = findSpawnLocations(gameWorld, participants.size)
            if (candidates.size == participants.size) {
                spawnPoints = candidates
                break
            }
        }
        if (!foundCenter) {
            sendNotification("바다가 아닌 안전한 자기장 중심을 찾지 못해 게임을 종료합니다.")
            stop()
            return
        }
        if (spawnPoints.size != participants.size) {
            sendNotification("자기장 내부에서 서로 겹치지 않는 안전한 스폰 지점을 충분히 찾지 못해 게임을 종료합니다.")
            stop()
            return
        }
        spawnLocations.clear()
        spawnLocations.addAll(spawnPoints)

        var remaining = settings.countdownSeconds
        val task = object : BukkitRunnable() {
            override fun run() {
                if (phase != GamePhase.COUNTDOWN) {
                    cancel()
                    return
                }
                if (remaining <= 0) {
                    cancel()
                    scatterAndBegin()
                    return
                }
                participants.filter { !it.entityStatus.isDead && it.player.isOnline }.forEach { playerData ->
                    playerData.player.showTitle(
                        Title.title(
                            miniMessage.deserialize("<yellow><bold>$remaining"),
                            miniMessage.deserialize("<gray>잠시 후 무작위 위치로 산개합니다.")
                        )
                    )
                    playerData.player.playSound(
                        playerData.player.location,
                        Sound.BLOCK_NOTE_BLOCK_HAT,
                        SoundCategory.MASTER,
                        1.0F,
                        1.0F,
                    )
                }
                remaining--
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 20L)
        track(task)
    }

    private fun Game.scatterAndBegin() {
        phase = GamePhase.SCATTERING
        val participants = contenders()
        val boundary = absoluteSpawnBoundary(gameWorld)
        if (boundary == null || !areSpawnLocationsValid(gameWorld, spawnLocations, participants.size, boundary)) {
            val replacementLocations = findSpawnLocations(gameWorld, participants.size)
            spawnLocations.clear()
            spawnLocations.addAll(replacementLocations)
        }

        val resolvedBoundary = absoluteSpawnBoundary(gameWorld)
        if (resolvedBoundary == null ||
            !areSpawnLocationsValid(gameWorld, spawnLocations, participants.size, resolvedBoundary)
        ) {
            sendNotification("자기장 내부의 안전한 개별 스폰 지점을 확정하지 못해 게임을 종료합니다.")
            stop()
            return
        }

        assignedSpawnLocations.clear()
        val pendingTeleports = mutableListOf<Triple<PlayerData, Player, CompletableFuture<Boolean>>>()
        participants.zip(spawnLocations.shuffled()).forEach { (playerData, location) ->
            val playerId = playerData.player.uniqueId
            val destination = location
            assignedSpawnLocations[playerId] = destination.clone()
            val player = playerData.player
            if (!player.isOnline) return@forEach
            player.velocity = Vector()
            player.fallDistance = 0.0F
            val teleportFuture = player.teleportAsync(destination).exceptionally { error ->
                ClassWarPlugin.instance.logger.warning(
                    "[ClassWar] ${player.name} 산개 텔레포트 실패: ${error.message ?: error.javaClass.simpleName}",
                )
                false
            }
            pendingTeleports += Triple(playerData, player, teleportFuture)
        }

        if (pendingTeleports.isEmpty()) {
            beginBattle()
            return
        }

        val scatteringGame = this
        CompletableFuture.allOf(*pendingTeleports.map { it.third }.toTypedArray()).whenComplete { _, _ ->
            val plugin = ClassWarPlugin.instance
            if (!plugin.isEnabled) return@whenComplete
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (game !== scatteringGame || phase != GamePhase.SCATTERING) return@Runnable
                val failed = pendingTeleports.any { (_, teleportedPlayer, future) ->
                    teleportedPlayer.isOnline && future.getNow(false) != true
                }
                if (failed) {
                    sendNotification("월드보더 내부 산개에 실패하여 게임을 종료합니다.")
                    stop()
                    return@Runnable
                }
                pendingTeleports.forEach { (playerData, teleportedPlayer, future) ->
                    if (teleportedPlayer.isOnline && future.getNow(false) == true) {
                        initializeBattlePlayer(playerData)
                    }
                }
                beginBattle()
            })
        }
    }

    private fun Game.beginBattle() {
        phase = GamePhase.RUNNING
        contenders().forEach { livesRemaining.putIfAbsent(it.uniqueId, settings.playerLives) }
        if (mode.usesTailTagRules && tailTargets.isEmpty()) {
            initializeTailTargets(contenders())
        }
        contenders().forEach { playerData ->
            val status = playerData.entityStatus
            if (disconnectedPlayers.contains(playerData.player.uniqueId)) {
                disablePlayerInteraction(playerData)
                return@forEach
            }
            status.canAttack = true
            status.canSkillUse = true
            status.canMove = true
            status.isAttackable = true
            status.isSkillTargeting = true
            playerData.player.gameMode = GameMode.ADVENTURE
            playerData.player.showTitle(
                Title.title(
                    miniMessage.deserialize("<red><bold>Fight!"),
                    miniMessage.deserialize(
                        if (mode.usesTailTagRules) {
                            val targetName = targetOf(playerData.uniqueId)?.let { findParticipant(it) }?.player?.name
                                ?: "표적 없음"
                            "<gold>당신의 표적: <white><bold>$targetName"
                        } else {
                            "<gray>마지막 생존자가 되세요."
                        }
                    )
                )
            )
            if (mode.usesTailTagRules) sendTailTargetNotice(playerData)
        }
        sendNotification("${mode.displayName} <gray>게임이 시작되었습니다.")
        startClassTickTask()
        startTailHeartbeatTask()
        startWorldBorder()
    }

    private fun Game.initializeTailTargets(participants: List<PlayerData>) {
        tailTargets.clear()
        if (!mode.usesTailTagRules || participants.size <= 1) return
        val shuffledIds = participants.map { it.uniqueId }.shuffled()
        shuffledIds.forEachIndexed { index, playerId ->
            tailTargets[playerId] = shuffledIds[(index + 1) % shuffledIds.size]
        }
    }

    private fun Game.sendTailTargetNotice(playerData: PlayerData) {
        val target = targetOf(playerData.uniqueId)?.let { findParticipant(it) } ?: return
        if (!playerData.player.isOnline) return
        playerData.player.sendMessage(
            miniMessage.deserialize(
                "<gold><bold>[꼬리잡기]</bold> <gray>당신의 표적은 <white><bold>${target.player.name}</bold><gray>님입니다. " +
                    "<red>표적에게만 피해를 줄 수 있습니다."
            )
        )
    }

    private fun Game.startTailHeartbeatTask() {
        if (!mode.usesTailTagRules) return
        val nextHeartbeatTicks = mutableMapOf<UUID, Long>()
        var elapsedTicks = 0L
        val task = object : BukkitRunnable() {
            override fun run() {
                if (phase != GamePhase.RUNNING) {
                    cancel()
                    return
                }
                if (isPaused) return
                elapsedTicks += 2L

                contenders().forEach { playerData ->
                    if (elapsedTicks % 10L == 0L) showTailTargetHighlight(playerData)
                    val player = playerData.player
                    val threat = threatOf(playerData.uniqueId)?.let { findParticipant(it) }
                    if (!player.isOnline || disconnectedPlayers.contains(playerData.uniqueId) ||
                        threat == null || threat.entityStatus.isDead || !threat.player.isOnline ||
                        disconnectedPlayers.contains(threat.uniqueId) || player.world != threat.player.world
                    ) {
                        nextHeartbeatTicks.remove(playerData.uniqueId)
                        return@forEach
                    }

                    val distanceSquared = player.location.distanceSquared(threat.player.location)
                    if (distanceSquared > TAIL_HEARTBEAT_RADIUS * TAIL_HEARTBEAT_RADIUS) {
                        nextHeartbeatTicks.remove(playerData.uniqueId)
                        return@forEach
                    }

                    val distance = sqrt(distanceSquared)
                    val proximity = (1.0 - distance / TAIL_HEARTBEAT_RADIUS).coerceIn(0.0, 1.0)
                    val interval = (
                        TAIL_HEARTBEAT_MAX_INTERVAL_TICKS -
                            (TAIL_HEARTBEAT_MAX_INTERVAL_TICKS - TAIL_HEARTBEAT_MIN_INTERVAL_TICKS) * proximity
                        ).roundToInt().coerceAtLeast(TAIL_HEARTBEAT_MIN_INTERVAL_TICKS)
                    val nextHeartbeat = nextHeartbeatTicks[playerData.uniqueId] ?: elapsedTicks
                    if (elapsedTicks < nextHeartbeat) return@forEach

                    val pitch = (0.75 + proximity * 0.4).toFloat()
                    val volume = (0.45 + proximity * 0.55).toFloat()
                    player.playSound(
                        player.location,
                        Sound.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.MASTER,
                        volume,
                        pitch,
                    )
                    nextHeartbeatTicks[playerData.uniqueId] = elapsedTicks + interval
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L)
        track(task)
    }

    /** 다른 참가자에게는 보이지 않는 파티클 윤곽으로 각 플레이어의 표적만 강조합니다. */
    private fun Game.showTailTargetHighlight(hunterData: PlayerData) {
        val hunter = hunterData.player
        val targetData = targetOf(hunterData.uniqueId)?.let { targetId -> findParticipant(targetId) } ?: return
        val target = targetData.player
        if (!hunter.isOnline || !target.isOnline || targetData.entityStatus.isDead ||
            disconnectedPlayers.contains(hunterData.uniqueId) || disconnectedPlayers.contains(targetData.uniqueId) ||
            hunter.world != target.world || !hunter.canSee(target)
        ) {
            return
        }

        val box = target.boundingBox
        val center = box.center
        val radius = maxOf(box.maxX - box.minX, box.maxZ - box.minZ) * 0.65 + 0.18
        repeat(12) { index ->
            val angle = index * (PI * 2.0 / 12.0)
            val y = when (index % 3) {
                0 -> box.minY + 0.12
                1 -> (box.minY + box.maxY) * 0.5
                else -> box.maxY - 0.08
            }
            hunter.spawnParticle(
                Particle.GLOW,
                Location(target.world, center.x + cos(angle) * radius, y, center.z + sin(angle) * radius),
                1,
                0.0,
                0.0,
                0.0,
                0.0,
            )
        }
        hunter.spawnParticle(
            Particle.END_ROD,
            Location(target.world, center.x, box.maxY + 0.22, center.z),
            2,
            0.08,
            0.05,
            0.08,
            0.0,
        )
    }

    private fun Game.startClassTickTask() {
        val task = object : BukkitRunnable() {
            override fun run() {
                if (phase != GamePhase.RUNNING) return
                if (isPaused) return
                contenders().filter {
                    !disconnectedPlayers.contains(it.player.uniqueId) &&
                        battleInitializedPlayers.contains(it.player.uniqueId)
                }.forEach { playerData ->
                    if (!playerData.initGame.isPaused) AbilityTree.handlers(playerData.gameClasses, GameStatusHandler::class.java)
                        .forEach { bound -> bound.call { it.onGameTimePasses() } }
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L)
        track(task)
    }

    private fun Game.startWorldBorder() {
        if (!settings.borderEnabled) return

        val border = gameWorld.worldBorder
        originalBorderCenter = border.center.clone()
        originalBorderSize = border.size
        border.setCenter(roundCenterX, roundCenterZ)
        border.damageBuffer = settings.borderDamageBuffer
        border.damageAmount = settings.borderDamagePerBlock
        val initialBorderSize = settings.borderInitialSize.coerceAtLeast(1.0)
        val targetBorderSize = settings.borderMinimumSize.coerceAtLeast(1.0)
        border.size = initialBorderSize

        val bossBar = BossBar.bossBar(
            miniMessage.deserialize("<aqua><bold>월드보더 축소 준비"),
            1.0F,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS,
        )
        borderBossBar = bossBar
        contenders().filter { it.player.isOnline }.forEach { it.player.showBossBar(bossBar) }

        val delayTicks = settings.borderDelaySeconds.toLong() * 20L
        val shrinkTicks = settings.borderShrinkSeconds.toLong() * 20L
        var elapsedTicks = 0L
        var shrinking = false
        var shrinkStartX = roundCenterX
        var shrinkStartZ = roundCenterZ
        var shrinkTargetX = roundCenterX
        var shrinkTargetZ = roundCenterZ
        var borderPaused = false
        val task = object : BukkitRunnable() {
            override fun run() {
                if (phase != GamePhase.RUNNING) {
                    cancel()
                    return
                }

                if (isPaused || MapTransferBorderManager.isExpanded(gameWorld)) {
                    if (!borderPaused && shrinking) border.changeSize(border.size, 0L)
                    borderPaused = true
                    return
                }
                if (borderPaused) {
                    borderPaused = false
                    if (shrinking) {
                        border.changeSize(targetBorderSize, (shrinkTicks - elapsedTicks).coerceAtLeast(0L))
                    }
                }

                if (!shrinking && elapsedTicks >= delayTicks) {
                    shrinking = true
                    elapsedTicks = 0L
                    shrinkStartX = border.center.x
                    shrinkStartZ = border.center.z
                    val maximumOffset = ((border.size - targetBorderSize) / 2.0).coerceAtLeast(0.0)
                    val offsetX = if (maximumOffset > 0.0) Random.nextDouble(-maximumOffset, maximumOffset) else 0.0
                    val offsetZ = if (maximumOffset > 0.0) Random.nextDouble(-maximumOffset, maximumOffset) else 0.0
                    shrinkTargetX = shrinkStartX + offsetX
                    shrinkTargetZ = shrinkStartZ + offsetZ
                    if (shrinkTicks <= 0L) {
                        border.size = targetBorderSize
                        border.setCenter(shrinkTargetX, shrinkTargetZ)
                        startFinalBorderDescent(shrinkTargetX, shrinkTargetZ, targetBorderSize, bossBar)
                        cancel()
                        return
                    }
                    border.changeSize(targetBorderSize, shrinkTicks)
                    bossBar.color(BossBar.Color.RED)
                }

                if (!shrinking) {
                    val total = delayTicks.coerceAtLeast(1L)
                    val remainingTicks = (delayTicks - elapsedTicks).coerceAtLeast(0L)
                    if (elapsedTicks % BORDER_BOSS_BAR_UPDATE_INTERVAL_TICKS == 0L || remainingTicks == 0L) {
                        val remaining = ((remainingTicks + 19L) / 20L).toInt()
                        bossBar.name(miniMessage.deserialize("<aqua><bold>월드보더 축소까지 ${formatTime(remaining)}"))
                        bossBar.progress((remainingTicks.toFloat() / total).coerceIn(0.0F, 1.0F))
                    }
                } else {
                    val progress = (elapsedTicks.toDouble() / shrinkTicks).coerceIn(0.0, 1.0)
                    border.setCenter(
                        shrinkStartX + (shrinkTargetX - shrinkStartX) * progress,
                        shrinkStartZ + (shrinkTargetZ - shrinkStartZ) * progress,
                    )
                    val remainingTicks = (shrinkTicks - elapsedTicks).coerceAtLeast(0L)
                    if (elapsedTicks % BORDER_BOSS_BAR_UPDATE_INTERVAL_TICKS == 0L || remainingTicks == 0L) {
                        val remaining = ((remainingTicks + 19L) / 20L).toInt()
                        bossBar.name(miniMessage.deserialize("<red><bold>월드보더 축소 중 ${formatTime(remaining)}"))
                        bossBar.progress((remainingTicks.toFloat() / shrinkTicks).coerceIn(0.0F, 1.0F))
                    }
                    if (elapsedTicks >= shrinkTicks) {
                        border.setCenter(shrinkTargetX, shrinkTargetZ)
                        startFinalBorderDescent(shrinkTargetX, shrinkTargetZ, targetBorderSize, bossBar)
                        cancel()
                        return
                    }
                }
                elapsedTicks++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        track(task)
    }

    private fun Game.startFinalBorderDescent(
        centerX: Double,
        centerZ: Double,
        borderSize: Double,
        bossBar: BossBar,
    ) {
        clearFinalBorderDisplays()

        val world = gameWorld
        val initialCoveredSize = borderSize.coerceAtLeast(1.0)
        val targetCoveredSize = settings.borderInitialSize.coerceAtLeast(initialCoveredSize)
        val tilesPerAxis = ceil(targetCoveredSize / FINAL_BORDER_DISPLAY_TILE_SIZE).toInt()
            .coerceIn(1, FINAL_BORDER_MAX_TILES_PER_AXIS)
        val tileSpan = initialCoveredSize / tilesPerAxis
        val visibleTileSpan = (tileSpan * 0.965).toFloat()
        val tileInset = ((tileSpan - visibleTileSpan) * 0.5).toFloat()
        val startY = (world.maxHeight - 1).toDouble()
        val endY = (world.minHeight + 1).toDouble()
        val minimumX = centerX - initialCoveredSize * 0.5
        val minimumZ = centerZ - initialCoveredSize * 0.5

        repeat(tilesPerAxis) { xIndex ->
            repeat(tilesPerAxis) { zIndex ->
                val location = Location(
                    world,
                    minimumX + xIndex * tileSpan,
                    startY,
                    minimumZ + zIndex * tileSpan,
                )
                val display = world.spawn(location, BlockDisplay::class.java).apply {
                    block = Material.RED_STAINED_GLASS.createBlockData()
                    isPersistent = false
                    brightness = Display.Brightness(15, 15)
                    viewRange = 6.0F
                    shadowStrength = 0.0F
                    interpolationDuration = FINAL_BORDER_UPDATE_INTERVAL_TICKS.toInt()
                    teleportDuration = FINAL_BORDER_UPDATE_INTERVAL_TICKS.toInt()
                    transformation = Transformation(
                        Vector3f(tileInset, -0.16F, tileInset),
                        Quaternionf(),
                        Vector3f(visibleTileSpan, 0.32F, visibleTileSpan),
                        Quaternionf(),
                    )
                }
                finalBorderDisplays += display
            }
        }

        borderBossBar = bossBar
        bossBar.color(BossBar.Color.RED)
        bossBar.progress(1.0F)
        val descentSeconds = settings.finalBorderDescentSeconds
        bossBar.name(miniMessage.deserialize("<dark_red><bold>상공 자기장 확장·하강 중 ${formatTime(descentSeconds)}"))
        sendNotification("<dark_red><bold>최종 자기장이 맵 전체로 확장되며 상공에서 하강하기 시작합니다.")
        activePlayers().filter { it.player.isOnline }.forEach { playerData ->
            playerData.player.playSound(
                playerData.player.location,
                Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                SoundCategory.MASTER,
                0.65F,
                0.62F,
            )
            playerData.player.playSound(
                playerData.player.location,
                Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                SoundCategory.MASTER,
                0.8F,
                0.55F,
            )
        }

        val totalTicks = descentSeconds * 20L
        var elapsedTicks = 0L
        var descentCompleted = false
        val lastDamageTicks = mutableMapOf<UUID, Long>()
        val descentTask = object : BukkitRunnable() {
            override fun run() {
                if (phase != GamePhase.RUNNING) {
                    clearFinalBorderDisplays()
                    activePlayers().filter { it.player.isOnline }.forEach { it.player.hideBossBar(bossBar) }
                    if (borderBossBar === bossBar) borderBossBar = null
                    cancel()
                    return
                }
                if (isPaused || MapTransferBorderManager.isExpanded(world)) return

                val progress = if (totalTicks == 0L) {
                    1.0
                } else {
                    (elapsedTicks.toDouble() / totalTicks).coerceIn(0.0, 1.0)
                }
                val easedProgress = progress * progress * (3.0 - 2.0 * progress)
                val currentY = startY + (endY - startY) * progress
                val currentCenterX = centerX + (roundCenterX - centerX) * easedProgress
                val currentCenterZ = centerZ + (roundCenterZ - centerZ) * easedProgress
                val currentCoveredSize = initialCoveredSize +
                    (targetCoveredSize - initialCoveredSize) * easedProgress
                if (!descentCompleted) {
                    updateFinalBorderDisplays(
                        currentCenterX,
                        currentCenterZ,
                        currentY,
                        currentCoveredSize,
                        tilesPerAxis,
                    )
                }
                applyFinalBorderDamage(
                    currentCenterX,
                    currentCenterZ,
                    currentY,
                    currentCoveredSize,
                    lastDamageTicks,
                    descentCompleted,
                )
                if (phase != GamePhase.RUNNING) {
                    cancel()
                    return
                }

                val remainingTicks = (totalTicks - elapsedTicks).coerceAtLeast(0L)
                if (!descentCompleted) {
                    bossBar.progress((1.0 - progress).toFloat().coerceIn(0.0F, 1.0F))
                }
                if (!descentCompleted && elapsedTicks % 20L == 0L) {
                    val remainingSeconds = ((remainingTicks + 19L) / 20L).toInt()
                    bossBar.name(miniMessage.deserialize(
                        "<dark_red><bold>상공 자기장 확장·하강 중 ${formatTime(remainingSeconds)}"
                    ))
                }

                if (!descentCompleted && elapsedTicks >= totalTicks) {
                    descentCompleted = true
                    finalBorderCompleted = true
                    bossBar.progress(0.0F)
                    bossBar.name(miniMessage.deserialize("<red><bold>최종 자기장이 맵 전체를 덮었습니다"))
                    activePlayers().filter { it.player.isOnline }.forEach { playerData ->
                        playerData.player.playSound(
                            playerData.player.location,
                            Sound.BLOCK_BEACON_DEACTIVATE,
                            SoundCategory.MASTER,
                            0.9F,
                            0.5F,
                        )
                    }
                }
                if (!descentCompleted) {
                    elapsedTicks = (elapsedTicks + FINAL_BORDER_UPDATE_INTERVAL_TICKS).coerceAtMost(totalTicks)
                }
            }
        }.runTaskTimer(
            ClassWarPlugin.instance,
            0L,
            FINAL_BORDER_UPDATE_INTERVAL_TICKS,
        )
        track(descentTask)
    }

    private fun Game.updateFinalBorderDisplays(
        centerX: Double,
        centerZ: Double,
        y: Double,
        coveredSize: Double,
        tilesPerAxis: Int,
    ) {
        val tileSpan = coveredSize / tilesPerAxis
        val visibleTileSpan = (tileSpan * 0.965).toFloat()
        val tileInset = ((tileSpan - visibleTileSpan) * 0.5).toFloat()
        val minimumX = centerX - coveredSize * 0.5
        val minimumZ = centerZ - coveredSize * 0.5
        finalBorderDisplays.forEachIndexed { index, display ->
            if (!display.isValid) return@forEachIndexed
            val xIndex = index / tilesPerAxis
            val zIndex = index % tilesPerAxis
            display.transformation = Transformation(
                Vector3f(tileInset, -0.16F, tileInset),
                Quaternionf(),
                Vector3f(visibleTileSpan, 0.32F, visibleTileSpan),
                Quaternionf(),
            )
            display.teleport(
                Location(
                    gameWorld,
                    minimumX + xIndex * tileSpan,
                    y,
                    minimumZ + zIndex * tileSpan,
                )
            )
        }
    }

    private fun Game.applyFinalBorderDamage(
        centerX: Double,
        centerZ: Double,
        fieldY: Double,
        coveredSize: Double,
        lastDamageTicks: MutableMap<UUID, Long>,
        fieldCompleted: Boolean,
    ) {
        val halfSize = coveredSize * 0.5
        val minimumX = centerX - halfSize
        val maximumX = centerX + halfSize
        val minimumZ = centerZ - halfSize
        val maximumZ = centerZ + halfSize
        val currentTick = Bukkit.getCurrentTick().toLong()
        val damageIntervalTicks = (settings.finalBorderDamageIntervalSeconds * 20.0)
            .roundToInt()
            .toLong()
            .coerceAtLeast(1L)
        val insidePlayers = mutableSetOf<UUID>()

        contenders().filter { it.player.isOnline && it.player.world == gameWorld }.forEach { playerData ->
            if (phase != GamePhase.RUNNING) return
            val player = playerData.player
            val box = player.boundingBox
            val insideField = fieldCompleted ||
                (box.maxX >= minimumX && box.minX <= maximumX &&
                    box.maxZ >= minimumZ && box.minZ <= maximumZ &&
                    box.maxY >= fieldY - 0.16)
            if (!insideField) return@forEach

            insidePlayers += player.uniqueId
            val lastDamageTick = lastDamageTicks[player.uniqueId]
            if (lastDamageTick != null && currentTick - lastDamageTick < damageIntervalTicks) {
                return@forEach
            }
            lastDamageTicks[player.uniqueId] = currentTick
            applyFixedFinalBorderDamage(playerData)
            player.world.spawnParticle(
                org.bukkit.Particle.BLOCK,
                player.boundingBox.center.toLocation(player.world),
                14,
                0.45,
                0.75,
                0.45,
                0.04,
                Material.RED_STAINED_GLASS.createBlockData(),
            )
            player.playSound(
                player.location,
                Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT,
                SoundCategory.MASTER,
                0.32F,
                0.62F,
            )
            if (phase != GamePhase.RUNNING) return
        }
        lastDamageTicks.keys.retainAll(insidePlayers)
    }

    private fun Game.applyFixedFinalBorderDamage(playerData: PlayerData) {
        val player = playerData.player
        val appliedDamage = (settings.finalBorderDamage * settings.damageMultiplier(DamageMultiplierType.WORLD_BORDER))
            .coerceAtMost(player.health)
        if (appliedDamage <= 0.0) return
        playerData.gameClasses.filterIsInstance<Grass>().forEach { it.suppressStealthFromDamage() }
        CombatManager.recordDamageTaken(playerData)
        DamageIndicatorManager.show(player, appliedDamage, settings.damageIndicatorsEnabled)
        player.playHurtAnimation(0.0F)
        player.health = (player.health - appliedDamage).coerceAtLeast(0.0)
    }

    private fun Game.clearFinalBorderDisplays() {
        finalBorderDisplays.toList().forEach { display ->
            if (display.isValid) display.remove()
        }
        finalBorderDisplays.clear()
    }

    private fun Game.selectRandomRoundCenter(): Boolean {
        roundCenterX = settings.centerX
        roundCenterZ = settings.centerZ
        if (!settings.borderEnabled) return true

        val minimumOffset = minOf(settings.borderCenterMinimumDistance, settings.borderCenterMaximumDistance)
        val maximumOffset = maxOf(settings.borderCenterMinimumDistance, settings.borderCenterMaximumDistance)
        if (maximumOffset == 0.0) {
            return isSuitableRoundCenter(gameWorld, settings.centerX, settings.centerZ)
        }
        repeat(ROUND_CENTER_SEARCH_ATTEMPTS) {
            val angle = Random.nextDouble(0.0, PI * 2.0)
            val radius = if (minimumOffset < maximumOffset) {
                sqrt(Random.nextDouble(minimumOffset * minimumOffset, maximumOffset * maximumOffset))
            } else {
                minimumOffset
            }
            val candidateX = settings.centerX + cos(angle) * radius
            val candidateZ = settings.centerZ + sin(angle) * radius
            if (isSuitableRoundCenter(gameWorld, candidateX, candidateZ)) {
                roundCenterX = candidateX
                roundCenterZ = candidateZ
                return true
            }
        }

        return isSuitableRoundCenter(gameWorld, settings.centerX, settings.centerZ)
    }

    private fun Game.findSpawnLocations(world: World, count: Int): List<Location> {
        if (count <= 0) return emptyList()
        val selectionDeadline = System.nanoTime() + SPAWN_SELECTION_TIME_BUDGET_NANOS
        val canRecenterRound = phase == GamePhase.COUNTDOWN || phase == GamePhase.SCATTERING
        var boundary = ensureSpawnBoundary(world, canRecenterRound)
        var centralNodes = centralSpawnNodes(world, boundary)
        if (canRecenterRound && centralNodes.isEmpty() && System.nanoTime() < selectionDeadline) {
            findReplacementRoundCenter(world, boundary, selectionDeadline)?.let { replacement ->
                roundCenterX = replacement.x
                roundCenterZ = replacement.z
                boundary = ensureSpawnBoundary(world, allowRoundRecenter = true)
                centralNodes = centralSpawnNodes(world, boundary)
            }
        }
        val centralLocations = centralNodes.map { PlayerNavigation.playerLocation(world, it) }
        val targetNodes = centralNodes.toHashSet()
        val fallbackBoundary = fallbackSpawnBoundary(boundary)
        val result = mutableListOf<Location>()
        val relaxedCandidates = mutableListOf<Location>()
        val minimumDistanceSquared = settings.minimumPlayerDistance * settings.minimumPlayerDistance

        fun evaluateCandidate(x: Int, z: Int): Location? {
            if (System.nanoTime() >= selectionDeadline) return null
            val candidate = safeSpawnLocation(world, x, z) ?: return null
            val start = spawnCandidateNode(world, candidate, result, boundary) ?: return null
            if ((result + relaxedCandidates).none { it.distanceSquared(candidate) < minimumDistanceSquared }) {
                relaxedCandidates += candidate.clone()
            }
            val pathDeadline = minOf(
                selectionDeadline,
                System.nanoTime() + SPAWN_PATH_TIME_BUDGET_NANOS,
            )
            return candidate.takeIf {
                PlayerNavigation.hasPathToArea(
                    world = world,
                    start = start,
                    centerX = roundCenterX,
                    centerZ = roundCenterZ,
                    targetRadius = SPAWN_CENTER_TARGET_RADIUS,
                    targetNodes = targetNodes,
                    bounds = boundary.toNavigationBounds(),
                    maxVisitedNodes = SPAWN_PATH_MAX_VISITED_NODES,
                    deadlineNanos = pathDeadline,
                )
            }
        }

        for (index in 0 until count) {
            var selected: Location? = null
            for (attempt in 0 until SPAWN_SEARCH_ATTEMPTS) {
                if (System.nanoTime() >= selectionDeadline) break
                val angle = Random.nextDouble(0.0, PI * 2.0)
                val minimumRadius = minOf(settings.scatterMinRadius, settings.scatterMaxRadius)
                val maximumRadius = maxOf(settings.scatterMinRadius, settings.scatterMaxRadius)
                val radius = if (minimumRadius == maximumRadius) {
                    minimumRadius
                } else {
                    sqrt(Random.nextDouble(minimumRadius * minimumRadius, maximumRadius * maximumRadius))
                }
                val x = floor(roundCenterX + cos(angle) * radius).toInt()
                val z = floor(roundCenterZ + sin(angle) * radius).toInt()
                selected = evaluateCandidate(x, z)
                if (selected != null) break
            }
            if (selected == null && System.nanoTime() < selectionDeadline) {
                for (attempt in 0 until SPAWN_BORDER_FALLBACK_ATTEMPTS) {
                    if (System.nanoTime() >= selectionDeadline) break
                    val x = randomBlockCoordinate(fallbackBoundary.minX, fallbackBoundary.maxX) ?: continue
                    val z = randomBlockCoordinate(fallbackBoundary.minZ, fallbackBoundary.maxZ) ?: continue
                    selected = evaluateCandidate(x, z)
                    if (selected != null) break
                }
            }
            if (selected == null) break
            result += selected
        }

        // A costly or blocked route may exceed the strict pathfinding budget. Such locations are
        // still safe walkable positions inside the border, so prefer them over stacking everyone
        // at the center while keeping the configured player spacing whenever possible.
        relaxedCandidates.shuffled().forEach { candidate ->
            if (result.size >= count) return@forEach
            if (result.none { it.distanceSquared(candidate) < minimumDistanceSquared }) {
                result += candidate.clone()
            }
        }

        // If strict scatter spacing cannot be satisfied, use distinct central walkable
        // nodes while relaxing only the configured player separation.
        val centralFallbacks = centralLocations.shuffled()
        centralFallbacks.forEach { candidate ->
            if (result.size >= count) return@forEach
            if (result.none { it.distanceSquared(candidate) < SPAWN_RELAXED_MINIMUM_DISTANCE_SQUARED }) {
                result += candidate.clone()
            }
        }

        // Search the remaining permitted area without requiring the expensive route check.
        // These are still verified land positions inside both the current border and the
        // initial magnetic field, and every result remains physically distinct.
        val fallbackDeadline = maxOf(selectionDeadline, System.nanoTime() + 100_000_000L)
        val fallbackAttempts = count * SPAWN_DISTINCT_FALLBACK_ATTEMPTS_PER_PLAYER
        for (attempt in 0 until fallbackAttempts) {
            if (result.size >= count || System.nanoTime() >= fallbackDeadline) break
            val x = randomBlockCoordinate(boundary.minX, boundary.maxX) ?: break
            val z = randomBlockCoordinate(boundary.minZ, boundary.maxZ) ?: break
            val candidate = safeSpawnLocation(world, x, z) ?: continue
            if (!world.worldBorder.isInside(candidate) || !boundary.contains(candidate)) continue
            if (result.any { it.distanceSquared(candidate) < SPAWN_RELAXED_MINIMUM_DISTANCE_SQUARED }) continue
            result += candidate
        }
        return result
    }

    private fun Game.ensureSpawnBoundary(world: World, allowRoundRecenter: Boolean = true): SpawnBoundary {
        absoluteSpawnBoundary(world)?.let { return it }

        // A stale or externally moved world border can be disjoint from the configured
        // random center. Re-anchor this round to the real border before scattering.
        if (allowRoundRecenter) {
            val currentCenter = world.worldBorder.center
            roundCenterX = currentCenter.x
            roundCenterZ = currentCenter.z
            absoluteSpawnBoundary(world)?.let { return it }
        }
        return currentBorderSpawnBoundary(world)
    }

    private fun Game.centralSpawnNodes(world: World, boundary: SpawnBoundary): List<PlayerNavigation.Node> {
        val radius = SPAWN_CENTER_TARGET_RADIUS
        val radiusSquared = radius * radius
        val minimumX = floor(roundCenterX - radius).toInt()
        val maximumX = floor(roundCenterX + radius).toInt()
        val minimumZ = floor(roundCenterZ - radius).toInt()
        val maximumZ = floor(roundCenterZ + radius).toInt()
        val exposed = mutableListOf<Pair<Double, PlayerNavigation.Node>>()
        val covered = mutableListOf<Pair<Double, PlayerNavigation.Node>>()

        for (x in minimumX..maximumX) {
            for (z in minimumZ..maximumZ) {
                val blockCenterX = x + 0.5
                val blockCenterZ = z + 0.5
                val dx = blockCenterX - roundCenterX
                val dz = blockCenterZ - roundCenterZ
                val distanceSquared = dx * dx + dz * dz
                if (distanceSquared > radiusSquared || !boundary.contains(blockCenterX, blockCenterZ)) continue
                val nodes = PlayerNavigation.spawnableLandNodesInColumn(
                    world,
                    x,
                    z,
                    SPAWN_COLUMN_SEARCH_DEPTH,
                )
                val naturalSurface = nodes.firstOrNull()?.let { isNaturalTerrainNode(world, it) } == true
                nodes.forEachIndexed { index, node ->
                    val location = PlayerNavigation.playerLocation(world, node)
                    if (!world.worldBorder.isInside(location) || !boundary.contains(location)) return@forEachIndexed
                    if (index == 0) {
                        exposed += distanceSquared to node
                    } else if (!naturalSurface) {
                        covered += distanceSquared to node
                    }
                }
            }
        }
        val preferred = covered + exposed
        return preferred.distinctBy { it.second }
            .sortedWith(compareBy<Pair<Double, PlayerNavigation.Node>> { it.first }.thenByDescending { it.second.y })
            .map { it.second }
    }

    private fun Game.centralSpawnLocations(world: World, boundary: SpawnBoundary): List<Location> =
        centralSpawnNodes(world, boundary).map { PlayerNavigation.playerLocation(world, it) }

    private fun Game.findReplacementRoundCenter(
        world: World,
        boundary: SpawnBoundary,
        deadlineNanos: Long,
    ): Location? {
        for (attempt in 0 until SPAWN_CENTER_REPLACEMENT_ATTEMPTS) {
            if (System.nanoTime() >= deadlineNanos) break
            val x = randomBlockCoordinate(boundary.minX, boundary.maxX) ?: continue
            val z = randomBlockCoordinate(boundary.minZ, boundary.maxZ) ?: continue
            if (!isSuitableRoundCenter(world, x + 0.5, z + 0.5)) continue
            val natural = safeSpawnLocation(world, x, z)
            if (natural != null && world.worldBorder.isInside(natural) && boundary.contains(natural)) return natural
            if (System.nanoTime() >= deadlineNanos) break
            val node = PlayerNavigation.surfaceNode(world, x, z) ?: continue
            val navigable = PlayerNavigation.playerLocation(world, node)
            if (PlayerNavigation.isSpawnableLandNode(world, node) &&
                world.worldBorder.isInside(navigable) && boundary.contains(navigable)
            ) return navigable
        }
        return null
    }

    private fun Game.fallbackSpawnBoundary(boundary: SpawnBoundary): SpawnBoundary {
        val configuredRadius = maxOf(settings.scatterMinRadius, settings.scatterMaxRadius)
        val searchRadius = maxOf(configuredRadius, SPAWN_CENTER_TARGET_RADIUS * 8.0)
            .coerceAtMost(SPAWN_BORDER_FALLBACK_MAX_RADIUS)
        val anchorX = roundCenterX.coerceIn(boundary.minX, boundary.maxX)
        val anchorZ = roundCenterZ.coerceIn(boundary.minZ, boundary.maxZ)
        return SpawnBoundary(
            minX = maxOf(boundary.minX, anchorX - searchRadius),
            maxX = minOf(boundary.maxX, anchorX + searchRadius),
            minZ = maxOf(boundary.minZ, anchorZ - searchRadius),
            maxZ = minOf(boundary.maxZ, anchorZ + searchRadius),
        )
    }

    private fun Game.absoluteSpawnBoundary(world: World): SpawnBoundary? {
        val currentBorder = world.worldBorder
        val currentCenter = currentBorder.center
        val currentHalfSize = currentBorder.size / 2.0
        var minX = currentCenter.x - currentHalfSize + SPAWN_BORDER_MARGIN
        var maxX = currentCenter.x + currentHalfSize - SPAWN_BORDER_MARGIN
        var minZ = currentCenter.z - currentHalfSize + SPAWN_BORDER_MARGIN
        var maxZ = currentCenter.z + currentHalfSize - SPAWN_BORDER_MARGIN

        if (settings.borderEnabled) {
            val initialHalfSize = settings.borderInitialSize.coerceAtLeast(1.0) / 2.0
            minX = maxOf(minX, roundCenterX - initialHalfSize + SPAWN_BORDER_MARGIN)
            maxX = minOf(maxX, roundCenterX + initialHalfSize - SPAWN_BORDER_MARGIN)
            minZ = maxOf(minZ, roundCenterZ - initialHalfSize + SPAWN_BORDER_MARGIN)
            maxZ = minOf(maxZ, roundCenterZ + initialHalfSize - SPAWN_BORDER_MARGIN)
        }

        return if (minX <= maxX && minZ <= maxZ) {
            SpawnBoundary(minX, maxX, minZ, maxZ)
        } else {
            null
        }
    }

    private fun currentBorderSpawnBoundary(world: World): SpawnBoundary {
        val border = world.worldBorder
        val center = border.center
        val halfSize = (border.size / 2.0 - SPAWN_BORDER_MARGIN).coerceAtLeast(0.0)
        return SpawnBoundary(
            minX = center.x - halfSize,
            maxX = center.x + halfSize,
            minZ = center.z - halfSize,
            maxZ = center.z + halfSize,
        )
    }

    private fun Game.isAbsoluteSpawnLocation(location: Location): Boolean {
        if (location.world != gameWorld) return false
        val boundary = absoluteSpawnBoundary(gameWorld)
            ?: if (phase == GamePhase.RUNNING) currentBorderSpawnBoundary(gameWorld) else return false
        return gameWorld.worldBorder.isInside(location) && boundary.contains(location)
    }

    private fun Game.spawnCandidateNode(
        world: World,
        candidate: Location,
        existing: List<Location>,
        boundary: SpawnBoundary,
    ): PlayerNavigation.Node? {
        if (candidate.world != world || !world.worldBorder.isInside(candidate) || !boundary.contains(candidate)) return null
        val minimumDistanceSquared = settings.minimumPlayerDistance * settings.minimumPlayerDistance
        if (existing.any { it.distanceSquared(candidate) < minimumDistanceSquared }) return null
        val node = PlayerNavigation.nearestNode(world, candidate, verticalSearch = 0) ?: return null
        return node.takeIf { PlayerNavigation.isSpawnableLandNode(world, it) }
    }

    private fun Game.areSpawnLocationsValid(
        world: World,
        locations: List<Location>,
        expectedCount: Int,
        boundary: SpawnBoundary,
    ): Boolean {
        if (locations.size != expectedCount) return false
        val accepted = mutableListOf<Location>()
        locations.forEach { candidate ->
            if (candidate.world != world || !world.worldBorder.isInside(candidate) || !boundary.contains(candidate)) {
                return false
            }
            if (accepted.any { it.distanceSquared(candidate) < SPAWN_RELAXED_MINIMUM_DISTANCE_SQUARED }) return false
            val node = PlayerNavigation.nearestNode(world, candidate, verticalSearch = 0) ?: return false
            if (!PlayerNavigation.isSpawnableLandNode(world, node)) return false
            accepted += candidate
        }
        return true
    }

    private fun SpawnBoundary.toNavigationBounds(): PlayerNavigation.Bounds = PlayerNavigation.Bounds(
        minX = minX,
        maxX = maxX,
        minZ = minZ,
        maxZ = maxZ,
    )

    private fun randomBlockCoordinate(minimum: Double, maximum: Double): Int? {
        val minimumBlock = ceil(minimum - 0.5).toInt()
        val maximumBlock = floor(maximum - 0.5).toInt()
        if (minimumBlock > maximumBlock) return null
        return if (minimumBlock == maximumBlock) {
            minimumBlock
        } else {
            Random.nextInt(minimumBlock, maximumBlock + 1)
        }
    }

    private fun safeSpawnLocation(world: World, x: Int, z: Int): Location? {
        world.getChunkAt(x shr 4, z shr 4).load()
        val nodes = PlayerNavigation.spawnableLandNodesInColumn(world, x, z, SPAWN_COLUMN_SEARCH_DEPTH)
        val node = nodes.firstOrNull() ?: return null
        if (isNaturalTerrainNode(world, node)) return PlayerNavigation.playerLocation(world, node)

        // A constructed roof is not a valid scatter surface. If the same column
        // contains a protected floor, use that floor instead of the rooftop.
        return nodes.drop(1).firstOrNull()?.let { PlayerNavigation.playerLocation(world, it) }
    }

    private fun isNaturalTerrainNode(world: World, node: PlayerNavigation.Node): Boolean {
        val y = node.y - 1
        return y - 3 >= world.minHeight && y + 2 < world.maxHeight &&
            isNaturalGround(world.getBlockAt(node.x, y, node.z)) &&
            world.getBlockAt(node.x, y + 1, node.z).isPassable &&
            world.getBlockAt(node.x, y + 2, node.z).isPassable &&
            (1..3).all { depth -> isNaturalGround(world.getBlockAt(node.x, y - depth, node.z)) }
    }

    private fun isSuitableRoundCenter(world: World, centerX: Double, centerZ: Double): Boolean {
        val border = world.worldBorder
        val centerLocation = Location(world, centerX, world.minHeight.toDouble(), centerZ)
        if (!border.isInside(centerLocation)) return false
        val centerNode = PlayerNavigation.surfaceNode(world, floor(centerX).toInt(), floor(centerZ).toInt())
        if (centerNode == null || !PlayerNavigation.isSpawnableLandNode(world, centerNode)) return false

        var sampledColumns = 0
        var dryColumns = 0
        for (offsetX in -ROUND_CENTER_LAND_CHECK_RADIUS..ROUND_CENTER_LAND_CHECK_RADIUS step ROUND_CENTER_LAND_CHECK_STEP) {
            for (offsetZ in -ROUND_CENTER_LAND_CHECK_RADIUS..ROUND_CENTER_LAND_CHECK_RADIUS step ROUND_CENTER_LAND_CHECK_STEP) {
                if (offsetX * offsetX + offsetZ * offsetZ > ROUND_CENTER_LAND_CHECK_RADIUS * ROUND_CENTER_LAND_CHECK_RADIUS) continue
                sampledColumns++
                val x = floor(centerX + offsetX).toInt()
                val z = floor(centerZ + offsetZ).toInt()
                val node = PlayerNavigation.surfaceNode(world, x, z) ?: continue
                if (PlayerNavigation.isSpawnableLandNode(world, node)) dryColumns++
            }
        }
        return sampledColumns > 0 && dryColumns.toDouble() / sampledColumns >= ROUND_CENTER_MINIMUM_DRY_RATIO
    }

    private fun isNaturalGround(block: Block): Boolean {
        if (!block.type.isSolid) return false
        val name = block.type.name
        return block.type in naturalGroundMaterials ||
            name.endsWith("_TERRACOTTA") ||
            name.endsWith("_NYLIUM") ||
            name.endsWith("_ORE")
    }

    private val naturalGroundMaterials = setOf(
        Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
        Material.MYCELIUM, Material.ROOTED_DIRT, Material.MUD, Material.CLAY,
        Material.STONE, Material.DEEPSLATE, Material.GRANITE, Material.DIORITE,
        Material.ANDESITE, Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK,
        Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.SANDSTONE,
        Material.RED_SANDSTONE, Material.SNOW_BLOCK, Material.NETHERRACK,
        Material.END_STONE, Material.BASALT, Material.BLACKSTONE,
    )

    /** 실행 중인 경기의 참가자를 사망 처리하고 승리 조건을 다시 평가한다. */
    fun handleDeath(playerData: PlayerData) {
        val currentGame = playerData.initGame
        if (currentGame.phase != GamePhase.RUNNING || playerData.entityStatus.isDead) return
        currentGame.removeTailParticipant(playerData.uniqueId)
        playerData.entityStatus.isDead = true
        playerData.entityStatus.canAttack = false
        playerData.entityStatus.canSkillUse = false
        playerData.entityStatus.isAttackable = false
        playerData.entityStatus.isSkillTargeting = false
        StealthVisibilityManager.reveal(playerData)
        StealthVisibilityManager.revealTo(playerData.player)
        TemporaryDisplayManager.clear(playerData.player.world, playerData.uniqueId)
        unregisterAllTickingStatuses(playerData.statusAbnormalitys)
        playerData.statusAbnormalitys.clear()
        playerData.bukkitTasks.toList().forEach { it.cancel() }
        playerData.bukkitTasks.clear()
        AbilityTree.end(playerData.gameClasses, EndReason.DEATH)
        playerData.player.gameMode = GameMode.SPECTATOR

        val survivors = currentGame.contenders()
        if (survivors.size <= 1) {
            val pendingExplosionTicks = Terrorist.pendingExplosionTicks(currentGame)
            if (pendingExplosionTicks > 0L && Terrorist.markFinishScheduled(currentGame)) {
                val task = object : BukkitRunnable() {
                    override fun run() {
                        if (currentGame.isPaused || Terrorist.pendingExplosionTicks(currentGame) > 0L) return
                        cancel()
                        Terrorist.clearPending(currentGame)
                        if (currentGame.phase == GamePhase.RUNNING) {
                            currentGame.finish(currentGame.contenders().firstOrNull())
                        }
                    }
                }.runTaskTimer(ClassWarPlugin.instance, pendingExplosionTicks + 2L, 1L)
                currentGame.track(task)
            } else if (pendingExplosionTicks <= 0L) {
                currentGame.finish(survivors.firstOrNull())
            }
        }
    }

    /** 남은 목숨과 최종 자기장 상태를 반영해 재투입 또는 영구 탈락을 처리한다. */
    fun handleCombatDeath(playerData: PlayerData): DeathOutcome {
        val currentGame = playerData.initGame
        val remainingLives = currentGame.livesRemaining.getOrPut(playerData.uniqueId) {
            currentGame.settings.playerLives
        }
        if (currentGame.phase != GamePhase.RUNNING || currentGame.finalBorderCompleted || remainingLives <= 0) {
            handleDeath(playerData)
            return DeathOutcome.TERMINATE
        }

        currentGame.livesRemaining[playerData.uniqueId] = remainingLives - 1
        currentGame.prepareForRespawn(playerData)
        return DeathOutcome.BREAK
    }

    /** 설정이 켜진 경우 실제 처치자에게 결과 타이틀과 최대 체력 비례 회복을 지급한다. */
    fun rewardKiller(killerId: UUID?, victimId: UUID, outcome: DeathOutcome) {
        val currentGame = game ?: return
        if (!currentGame.settings.eliminationRewardsEnabled) return
        val creditedId = killerId?.takeIf { it != victimId } ?: return
        val killerData = currentGame.findParticipant(creditedId)
            ?.takeUnless { it.entityStatus.isDead } ?: return
        val killer = killerData.player.takeIf { it.isOnline } ?: return
        val fraction = if (outcome == DeathOutcome.TERMINATE) 0.5 else 0.35
        val maximumHealth = killer.getAttribute(Attribute.MAX_HEALTH)?.value ?: killer.getPlayerMaxHealth()
        killer.health = (killer.health + maximumHealth * fraction).coerceAtMost(maximumHealth)
        killer.showTitle(
            Title.title(
                miniMessage.deserialize(if (outcome == DeathOutcome.TERMINATE) "<dark_red><bold>TERMINATE" else "<gold><bold>BREAK"),
                Component.empty(),
            )
        )
    }

    private fun Game.prepareForRespawn(playerData: PlayerData) {
        disablePlayerInteraction(playerData)
        StealthVisibilityManager.reveal(playerData)
        StealthVisibilityManager.revealTo(playerData.player)
        TemporaryDisplayManager.clear(playerData.player.world, playerData.uniqueId)
        unregisterAllTickingStatuses(playerData.statusAbnormalitys)
        playerData.statusAbnormalitys.clear()
        playerData.bukkitTasks.toList().forEach { it.cancel() }
        playerData.bukkitTasks.clear()
        AbilityTree.handlers(playerData.gameClasses, PlayerDeathHandler::class.java)
            .forEach { bound -> bound.call { it.onPlayerDeath() } }

        val respawningGame = this
        object : BukkitRunnable() {
            override fun run() {
                if (game !== respawningGame || phase != GamePhase.RUNNING || !playerData.player.isOnline) return
                playerData.player.spigot().respawn()
                object : BukkitRunnable() {
                    override fun run() {
                        if (game !== respawningGame || phase != GamePhase.RUNNING || !playerData.player.isOnline) return
                        val destination = findRespawnLocation(playerData)
                        if (destination == null || !playerData.player.teleport(destination)) {
                            livesRemaining[playerData.uniqueId] = 0
                            handleDeath(playerData)
                            return
                        }
                        playerData.player.gameMode = GameMode.ADVENTURE
                        playerData.player.velocity = Vector()
                        playerData.player.fallDistance = 0.0F
                        playerData.entityStatus.canAttack = true
                        playerData.entityStatus.canSkillUse = true
                        playerData.entityStatus.canMove = true
                        playerData.entityStatus.isAttackable = true
                        playerData.entityStatus.isSkillTargeting = true
                        playerData.player.sendMessage(miniMessage.deserialize(
                            "<gold><bold>[BREAK]</bold> <gray>남은 목숨: <white>${livesRemaining[playerData.uniqueId] ?: 0}"
                        ))
                    }
                }.runTaskLater(ClassWarPlugin.instance, 1L).also { track(it) }
            }
        }.runTaskLater(ClassWarPlugin.instance, 1L).also { track(it) }
    }

    private fun Game.findRespawnLocation(playerData: PlayerData): Location? {
        val occupied = contenders()
            .filter { it.uniqueId != playerData.uniqueId && it.player.isOnline && it.player.world == gameWorld }
            .map { it.player.location }
        val minimumDistanceSquared = settings.minimumPlayerDistance * settings.minimumPlayerDistance
        repeat(8) {
            val candidate = findSpawnLocations(gameWorld, 1).firstOrNull() ?: return@repeat
            if (occupied.none { it.distanceSquared(candidate) < minimumDistanceSquared }) return candidate
        }
        return null
    }

    /** 유효한 타인 처치만 [playerKillCounts]에 기록한다. */
    fun Game.recordPlayerKill(victimId: UUID, killerId: UUID?) {
        val creditedKillerId = killerId?.takeIf { it != victimId } ?: return
        if (phase != GamePhase.RUNNING) return
        if (activePlayers().none { it.uniqueId == creditedKillerId }) return
        playerKillCounts[creditedKillerId] = (playerKillCounts[creditedKillerId] ?: 0) + 1
    }

    /** 참가자 연결 해제를 기록하고 5분 뒤에도 미복귀 시 탈락시키는 작업을 예약한다. */
    fun handleTemporaryDisconnect(player: Player) {
        val currentGame = game ?: return
        if (currentGame.phase == GamePhase.WAITING || currentGame.phase == GamePhase.FINISHED) return
        val playerData = currentGame.findParticipant(player.uniqueId) ?: return
        Contractor.clearSessions(listOf(player.uniqueId))
        DeathNote.clearSessions(listOf(player.uniqueId))
        Hacker.clearSessions(listOf(player.uniqueId))
        Mathematician.clearSessions(listOf(player.uniqueId))
        Vampire.clearForms(listOf(player.uniqueId))
        PortalGun.clearForPlayers(listOf(player.uniqueId))
        AreaDevelopment.clearDomains(listOf(player.uniqueId))
        if (!currentGame.disconnectedPlayers.add(player.uniqueId)) return
        AbilityTree.suspend(playerData.gameClasses.filter { it.isInjectedFor(playerData) })

        PlayerTagManager.clear(player)
        currentGame.disablePlayerInteraction(playerData)
        currentGame.borderBossBar?.let { player.hideBossBar(it) }

        if (playerData.entityStatus.isDead) return

        currentGame.sendNotification("${player.name}님이 게임에서 나갔습니다. 5분 안에 돌아오지 않으면 탈락합니다.")
        val task = object : BukkitRunnable() {
            override fun run() {
                currentGame.disconnectTasks.remove(player.uniqueId)
                if (game !== currentGame || currentGame.phase == GamePhase.FINISHED) return
                if (!currentGame.disconnectedPlayers.remove(player.uniqueId)) return
                if (playerData.entityStatus.isDead) return
                currentGame.permanentlyEliminateDisconnectedPlayer(playerData)
            }
        }.runTaskLater(ClassWarPlugin.instance, RECONNECT_GRACE_TICKS)
        currentGame.disconnectTasks[player.uniqueId] = task
        currentGame.tasks.add(task)
    }

    /** 참가자 엔티티를 새 [Player] 객체에 다시 연결하고 경기 단계에 맞는 상태를 복원한다. */
    fun handleReconnect(player: Player) {
        pendingPostGameCleanup.remove(player.uniqueId)?.let { snapshot ->
            restorePlayerAfterGame(player, snapshot)
        }

        val currentGame = game ?: return
        val playerData = currentGame.findParticipant(player.uniqueId)
        if (playerData == null) {
            if (currentGame.phase != GamePhase.FINISHED) {
                player.gameMode = GameMode.SPECTATOR
                currentGame.contenders().firstOrNull { it.player.isOnline }?.let { survivor ->
                    player.teleport(survivor.player.location)
                    player.spectatorTarget = survivor.player
                }
                player.sendMessage(miniMessage.deserialize("<gray><bold>[!] 진행 중인 게임을 관전합니다."))
            }
            return
        }
        currentGame.rebindPlayer(playerData, player)
        CooldownManager.refreshPlayer(player)
        currentGame.disconnectTasks.remove(player.uniqueId)?.cancel()
        currentGame.disconnectedPlayers.remove(player.uniqueId)
        PlayerTagManager.clear(player)

        if (currentGame.phase == GamePhase.FINISHED) {
            currentGame.playerSnapshots.remove(player.uniqueId)?.let { snapshot ->
                restorePlayerAfterGame(player, snapshot)
            }
            return
        }

        player.playerListName(miniMessage.deserialize(player.name))

        if (playerData.entityStatus.isDead) {
            player.gameMode = GameMode.SPECTATOR
            currentGame.contenders().firstOrNull { it.player.isOnline }?.let { survivor ->
                player.teleport(survivor.player.location)
                player.spectatorTarget = survivor.player
            }
            currentGame.borderBossBar?.let { player.showBossBar(it) }
            val message = if (currentGame.expiredReconnectPlayers.contains(player.uniqueId)) {
                "<red><bold>[!] 재접속 유예 시간이 지나 탈락 처리되었습니다. 관전 모드로 입장합니다."
            } else {
                "<gray><bold>[!] 이미 탈락한 참가자이므로 관전 모드로 입장합니다."
            }
            player.sendMessage(miniMessage.deserialize(message))
            return
        }

        when (currentGame.phase) {
            GamePhase.CLASS_SELECTION -> {
                player.gameMode = GameMode.ADVENTURE
                currentGame.disablePlayerInteraction(playerData)
                playerData.entityStatus.canMove = true
                if (!currentGame.confirmedPlayers.contains(player.uniqueId)) {
                    object : BukkitRunnable() {
                        override fun run() {
                            if (game === currentGame && currentGame.phase == GamePhase.CLASS_SELECTION && player.isOnline) {
                                playerData.openAssignedClassInventory()
                            }
                        }
                    }.runTaskLater(ClassWarPlugin.instance, 1L)
                }
            }

            GamePhase.COUNTDOWN -> {
                player.gameMode = GameMode.ADVENTURE
                currentGame.disablePlayerInteraction(playerData)
                playerData.entityStatus.canMove = false
            }

            GamePhase.SCATTERING, GamePhase.RUNNING -> {
                if (!currentGame.battleInitializedPlayers.contains(player.uniqueId)) {
                    val assigned = currentGame.assignedSpawnLocations[player.uniqueId]
                    val destination = assigned?.takeIf { currentGame.isAbsoluteSpawnLocation(it) }
                        ?: currentGame.findSpawnLocations(gameWorld, 1).firstOrNull()
                    if (destination == null || !currentGame.isAbsoluteSpawnLocation(destination) || !player.teleport(destination)) {
                        currentGame.disablePlayerInteraction(playerData)
                        player.gameMode = GameMode.SPECTATOR
                        player.sendMessage(miniMessage.deserialize("<red><bold>[!] 월드보더 내부의 안전한 복귀 지점을 찾지 못했습니다."))
                        return
                    }
                    currentGame.assignedSpawnLocations[player.uniqueId] = destination.clone()
                    currentGame.initializeBattlePlayer(playerData)
                }
                playerData.entityStatus.canAttack = true
                playerData.entityStatus.canSkillUse = true
                playerData.entityStatus.canMove = true
                playerData.entityStatus.isAttackable = true
                playerData.entityStatus.isSkillTargeting = true
                player.gameMode = GameMode.ADVENTURE
                currentGame.borderBossBar?.let { player.showBossBar(it) }
                playerData.restoreAbilityPosition()
                player.sendMessage(miniMessage.deserialize("<green><bold>[!] 게임에 정상적으로 복귀했습니다."))
                if (currentGame.mode.usesTailTagRules) currentGame.sendTailTargetNotice(playerData)
            }

            GamePhase.WAITING, GamePhase.FINISHED -> Unit
        }
    }

    /** 현재 경기 설정을 기준으로 탭 목록과 머리 위 이름표 가시성을 다시 적용한다. */
    fun refreshPlayerListVisibility() {
        val currentGame = game ?: return
        if (currentGame.settings.playerListVisible) PlayerListManager.restoreAll() else PlayerListManager.hideAll()
        NameTagManager.hideAll(currentGame.activePlayers().map { it.player.name })
    }

    private fun Game.permanentlyEliminateDisconnectedPlayer(playerData: PlayerData) {
        AbilityTree.end(playerData.gameClasses.filter { it.isInjectedFor(playerData) }, EndReason.REMOVED)
        removeTailParticipant(playerData.uniqueId)
        playerData.entityStatus.isDead = true
        StealthVisibilityManager.reveal(playerData)
        expiredReconnectPlayers.add(playerData.uniqueId)
        disablePlayerInteraction(playerData)
        playerData.bukkitTasks.toList().forEach { it.cancel() }
        playerData.bukkitTasks.clear()
        confirmedPlayers.remove(playerData.uniqueId)
        if (phase == GamePhase.CLASS_SELECTION) {
            playerData.gameClasses.forEach { assigned ->
                if (availableClasses.none { it.javaClass == assigned.javaClass }) availableClasses.add(assigned)
            }
        }
        sendNotification("${playerData.player.name}님이 5분 동안 돌아오지 않아 탈락했습니다.")

        val survivors = contenders()
        if (survivors.size <= 1) {
            finish(survivors.firstOrNull())
            return
        }
        if (phase == GamePhase.CLASS_SELECTION && survivors.all { confirmedPlayers.contains(it.uniqueId) }) {
            beginCountdown()
        }
    }

    private fun Game.removeTailParticipant(victimId: UUID) {
        if (!mode.usesTailTagRules || tailTargets.isEmpty()) return
        val successorId = tailTargets.remove(victimId)
        val hunterId = tailTargets.entries.firstOrNull { (_, targetId) -> targetId == victimId }?.key
        if (hunterId != null) tailTargets.remove(hunterId)

        val remaining = contenders().count { it.uniqueId != victimId }
        if (remaining <= 1) {
            tailTargets.clear()
            StealthVisibilityManager.refreshAll()
            return
        }

        if (hunterId != null && successorId != null && hunterId != successorId) {
            tailTargets[hunterId] = successorId
            findParticipant(hunterId)?.let { hunter ->
                sendTailTargetNotice(hunter)
                if (hunter.player.isOnline) {
                    hunter.player.playSound(
                        hunter.player.location,
                        Sound.BLOCK_NOTE_BLOCK_BELL,
                        SoundCategory.MASTER,
                        1.0F,
                        1.4F,
                    )
                }
            }
        }
        StealthVisibilityManager.refreshAll()
    }

    private fun Game.finish(winner: PlayerData?) {
        if (phase == GamePhase.FINISHED) return
        phase = GamePhase.FINISHED
        disconnectTasks.values.forEach { it.cancel() }
        disconnectTasks.clear()
        activePlayers().filter { it.player.isOnline }.forEach { playerData ->
            playerData.entityStatus.canAttack = false
            playerData.entityStatus.canSkillUse = false
            playerData.entityStatus.isAttackable = false
            playerData.entityStatus.isSkillTargeting = false
            playerData.player.clearGameGlowing()
            playerData.player.showTitle(
                Title.title(
                    miniMessage.deserialize("<gold><bold>${winner?.player?.name ?: "생존자 없음"}"),
                    miniMessage.deserialize("<gray>게임 종료")
                )
            )
        }
        broadcastClassSummary()
        val task = object : BukkitRunnable() {
            override fun run() = stop()
        }.runTaskLater(ClassWarPlugin.instance, 100L)
        track(task)
    }

    /**
     * 경기 작업·표시·상태·매니저 캐시를 정리하고 모든 참가자를 경기 전 스냅샷으로 복원한다.
     * 오프라인 참가자의 스냅샷은 다음 접속 때 복원하도록 보관한다.
     */
    fun Game.stop() {
        val wasRunning = phase == GamePhase.RUNNING
        phase = GamePhase.FINISHED
        if (wasRunning) broadcastClassSummary()
        val participantIds = activePlayers().map { it.uniqueId }
        activePlayers().forEach { data ->
            AbilityTree.end(data.gameClasses.filter { it.isInjectedFor(data) }, EndReason.GAME_END)
        }
        originalWorldTime?.let { gameWorld.time = it }
        originalDaylightCycle?.let { gameWorld.setGameRule(GameRules.ADVANCE_TIME, it) }
        GraveRobber.clearDeathRecords(this)
        Contractor.clearSessions(participantIds)
        DeathNote.clearSessions(participantIds)
        Hacker.clearSessions(participantIds)
        Mathematician.clearSessions(participantIds)
        Referee.clearSessions(participantIds)
        Vampire.clearForms(participantIds)
        PortalGun.clearForPlayers(participantIds)
        AreaDevelopment.clearDomains(participantIds)
        DamageManager.clearAttributions(participantIds)
        CombatManager.clear(participantIds)
        clearDamageInvincibility(participantIds)
        CooldownManager.clear(participantIds)
        DamageIndicatorManager.clearForPlayers(participantIds)
        BattleMapManager.cleanup(this)
        disconnectTasks.values.forEach { it.cancel() }
        disconnectTasks.clear()
        tasks.toList().forEach { it.cancel() }
        tasks.clear()
        clearFinalBorderDisplays()
        borderBossBar?.let { bar -> activePlayers().filter { it.player.isOnline }.forEach { it.player.hideBossBar(bar) } }
        borderBossBar = null
        gameWorld.worldBorder.reset()

        activePlayers().forEach { playerData ->
            StealthVisibilityManager.reveal(playerData)
            TemporaryDisplayManager.clear(playerData.player.world, playerData.uniqueId)
            unregisterAllTickingStatuses(playerData.statusAbnormalitys)
            playerData.statusAbnormalitys.clear()
            playerData.bukkitTasks.toList().forEach { it.cancel() }
            playerData.bukkitTasks.clear()
            val player = playerData.player
            player.clearGameGlowing()
            val snapshot = playerSnapshots.remove(player.uniqueId)
            if (player.isOnline) {
                if (snapshot != null) restorePlayerAfterGame(player, snapshot)
            } else if (snapshot != null) {
                pendingPostGameCleanup[player.uniqueId] = snapshot
            }
        }
        PlayerListManager.restoreAll()
        NameTagManager.restoreAll()
        playerDatas.filterNot { it is PlayerData }.forEach { entityData ->
            unregisterAllTickingStatuses(entityData.statusAbnormalitys)
            entityData.statusAbnormalitys.clear()
            entityData.bukkitTasks.toList().forEach { it.cancel() }
            entityData.bukkitTasks.clear()
        }
        playerSnapshots.clear()
        disconnectedPlayers.clear()
        expiredReconnectPlayers.clear()
        assignedSpawnLocations.clear()
        battleInitializedPlayers.clear()
        tailTargets.clear()
        availableClasses.clear()
        refreshesRemaining.clear()
        confirmedPlayers.clear()
        playerKillCounts.clear()
        spawnLocations.clear()
        roundCenterX = settings.centerX
        roundCenterZ = settings.centerZ
        originalBorderCenter = null
        originalBorderSize = null
        isPaused = false
        if (game === this) game = null
    }

    /** 훈련 태그가 있으면 개인 훈련 경기를, 그렇지 않으면 현재 정규 경기를 반환한다. */
    fun findGameForPlayer(player: Player): Game? =
        if (PlayerTagManager.isTraining(player)) {
            trainingInstance.find { instance -> instance.activePlayers().any { it.player == player } }
        } else {
            game
        }

    fun abilityClassSuggestions(): List<String> = gameClassList
        .map(ClassBalanceManager::configKey)
        .sorted()

    fun assignedAbilityNames(player: Player): List<String>? = findPlayerData(player)?.gameClasses
        ?.map(::plainClassName)

    fun tailTargetPlayer(player: Player): Player? {
        val currentGame = findGameForPlayer(player) ?: return null
        if (!currentGame.mode.usesTailTagRules) return null
        val playerData = currentGame.findParticipant(player.uniqueId) ?: return null
        return currentGame.targetOf(playerData.uniqueId)
            ?.let { targetId -> currentGame.findParticipant(targetId) }
            ?.player
    }

    /** 선택 또는 전투 중 [player]의 지정 슬롯에 운영자 요청으로 클래스를 배정한다. */
    fun forceAssignAbility(player: Player, query: String, slot: Int = 1): AbilityChangeResult {
        val currentGame = findGameForPlayer(player)
            ?: return AbilityChangeResult(false, "${player.name}님은 게임 또는 연습에 참가하고 있지 않습니다.")
        val playerData = currentGame.findParticipant(player.uniqueId)
            ?: return AbilityChangeResult(false, "${player.name}님의 게임 데이터를 찾을 수 없습니다.")
        if (currentGame.phase != GamePhase.CLASS_SELECTION && currentGame.phase != GamePhase.RUNNING) {
            return AbilityChangeResult(false, "능력은 선택 단계 또는 전투 진행 중에만 변경할 수 있습니다.")
        }
        if (slot !in 1..currentGame.mode.assignedClassCount) {
            return AbilityChangeResult(false, "${currentGame.mode.displayName} 모드에서는 1~${currentGame.mode.assignedClassCount}번 슬롯만 사용할 수 있습니다.")
        }

        val prototype = gameClassList.firstOrNull { matchesClassQuery(it, query) }
            ?: return AbilityChangeResult(false, "'$query'에 해당하는 능력을 찾을 수 없습니다.")
        if (!currentGame.mode.allowsParasite && prototype is Parasite) {
            return AbilityChangeResult(false, "꼬리잡기 모드에는 기생충을 배정할 수 없습니다.")
        }
        val slotIndex = slot - 1
        if (slotIndex > playerData.gameClasses.size) {
            return AbilityChangeResult(false, "앞 슬롯부터 먼저 배정해 주세요.")
        }
        if (playerData.gameClasses.withIndex().any { (index, assigned) ->
                index != slotIndex && assigned.javaClass == prototype.javaClass
            }
        ) {
            return AbilityChangeResult(false, "같은 능력을 한 플레이어에게 중복 배정할 수 없습니다.")
        }

        val replacement = if (currentGame.phase == GamePhase.CLASS_SELECTION) {
            currentGame.availableClasses.firstOrNull { it.javaClass == prototype.javaClass }
                ?: return AbilityChangeResult(false, "해당 능력은 이미 다른 플레이어에게 배정되었거나 사용할 수 없습니다.")
        } else {
            prototype
        }
        val previous = playerData.gameClasses.getOrNull(slotIndex)
        if (previous?.javaClass == replacement.javaClass) {
            return AbilityChangeResult(false, "이미 해당 슬롯에 ${plainClassName(previous)} 능력이 배정되어 있습니다.")
        }

        if (currentGame.phase == GamePhase.CLASS_SELECTION) {
            previous?.let { currentGame.availableClasses.add(it) }
            currentGame.availableClasses.remove(replacement)
            if (slotIndex == playerData.gameClasses.size) playerData.gameClasses.add(replacement)
            else playerData.gameClasses[slotIndex] = replacement
            currentGame.confirmedPlayers.remove(player.uniqueId)
            playerData.openAssignedClassInventory()
        } else {
            previous?.let(::endAssignedClass)
            if (slotIndex == playerData.gameClasses.size) playerData.gameClasses.add(replacement)
            else playerData.gameClasses[slotIndex] = replacement
            rebuildRuntimeAbilities(playerData, replacement)
        }

        return AbilityChangeResult(
            true,
            "${player.name}님의 ${slot}번 능력을 ${plainClassName(replacement)}(으)로 배정했습니다.",
        )
    }

    /** 슬롯 번호, 클래스 이름 또는 `all` 선택자로 운영자 요청 능력을 제거한다. */
    fun forceRemoveAbility(player: Player, selector: String): AbilityChangeResult {
        val currentGame = findGameForPlayer(player)
            ?: return AbilityChangeResult(false, "${player.name}님은 게임 또는 연습에 참가하고 있지 않습니다.")
        val playerData = currentGame.findParticipant(player.uniqueId)
            ?: return AbilityChangeResult(false, "${player.name}님의 게임 데이터를 찾을 수 없습니다.")
        if (currentGame.phase != GamePhase.CLASS_SELECTION && currentGame.phase != GamePhase.RUNNING) {
            return AbilityChangeResult(false, "능력은 선택 단계 또는 전투 진행 중에만 변경할 수 있습니다.")
        }
        if (playerData.gameClasses.isEmpty()) {
            return AbilityChangeResult(false, "${player.name}님에게 배정된 능력이 없습니다.")
        }

        val removed = when {
            selector.equals("all", ignoreCase = true) || selector == "전체" -> playerData.gameClasses.toList()
            selector.toIntOrNull() != null -> {
                val index = selector.toInt() - 1
                listOfNotNull(playerData.gameClasses.getOrNull(index))
            }
            else -> listOfNotNull(playerData.gameClasses.firstOrNull { matchesClassQuery(it, selector) })
        }
        if (removed.isEmpty()) {
            return AbilityChangeResult(false, "'$selector'에 해당하는 배정 능력을 찾을 수 없습니다.")
        }

        if (currentGame.phase == GamePhase.CLASS_SELECTION) {
            removed.forEach { gameClass ->
                playerData.gameClasses.remove(gameClass)
                if (currentGame.availableClasses.none { it.javaClass == gameClass.javaClass }) {
                    currentGame.availableClasses.add(gameClass)
                }
            }
            currentGame.confirmedPlayers.remove(player.uniqueId)
            if (playerData.gameClasses.isEmpty()) {
                PlayerTagManager.removeFlag(player, PlayerFlag.OPEN_ASSIGNED_CLASS_INVENTORY)
                player.closeInventory()
            } else {
                playerData.openAssignedClassInventory()
            }
        } else {
            removed.forEach(::endAssignedClass)
            playerData.gameClasses.removeAll(removed.toSet())
            rebuildRuntimeAbilities(playerData, initializedClass = null)
        }

        val removedNames = removed.joinToString(", ", transform = ::plainClassName)
        return AbilityChangeResult(true, "${player.name}님에게서 $removedNames 능력을 제거했습니다.")
    }

    private fun findPlayerData(player: Player): PlayerData? = findGameForPlayer(player)
        ?.playerDatas
        ?.filterIsInstance<PlayerData>()
        ?.firstOrNull { it.uniqueId == player.uniqueId }

    private fun rebuildRuntimeAbilities(playerData: PlayerData, initializedClass: GameClass?) {
        CooldownManager.clear(listOf(playerData.uniqueId))
        playerData.player.inventory.clear()
        if (playerData.gameClasses.isNotEmpty()) playerData.classSet(initializeHandlers = false)
        AbilityTree.start(playerData.gameClasses)
        playerData.player.inventory.heldItemSlot = 0
    }

    private fun endAssignedClass(gameClass: GameClass) {
        AbilityTree.end(listOf(gameClass), EndReason.REMOVED)
    }

    private fun matchesClassQuery(gameClass: GameClass, query: String): Boolean {
        val normalizedQuery = normalizeClassQuery(query)
        return sequenceOf(
            gameClass.javaClass.simpleName,
            ClassBalanceManager.configKey(gameClass),
            plainClassName(gameClass),
        ).any { normalizeClassQuery(it) == normalizedQuery }
    }

    private fun normalizeClassQuery(value: String): String = value
        .replace(miniMessageTagPattern, "")
        .replace(Regex("[\\s_-]+"), "")
        .lowercase(Locale.ROOT)

    private fun plainClassName(gameClass: GameClass): String = gameClass.name.replace(miniMessageTagPattern, "")

    /** 클래스 주입과 전투 초기화가 끝나 현재 클래스 처리기를 안전하게 호출할 수 있는지 판정한다. */
    fun PlayerData.canDispatchClassHandlers(): Boolean {
        if (gameClasses.isEmpty() || gameClasses.any { !it.isInjectedFor(this) }) return false
        if (!initGame.battleInitializedPlayers.contains(uniqueId)) return false
        return PlayerTagManager.isTraining(player) || initGame.phase == GamePhase.RUNNING
    }

    /** 원래 플레이어 상태를 보관하고 [gameClass]를 사용하는 개인 훈련 경기를 시작한다. */
    fun Player.startTraining(gameClass: GameClass) {
        val trainingGame = Game(mutableListOf())
        val playerData = PlayerData(this, trainingGame)
        trainingGame.playerSnapshots[uniqueId] = PlayerSnapshot.capture(this)
        trainingGame.playerDatas.add(playerData)
        playerData.gameClass = gameClass
        trainingInstance.add(trainingGame)
        PlayerTagManager.addFlag(this, PlayerFlag.TRAINING)
        Targeting.synchronizeTraining(playerData)
        playerData.classSet()
        trainingGame.battleInitializedPlayers.add(uniqueId)
        inventory.heldItemSlot = 0
        showTitle(Title.title(miniMessage.deserialize("<bold>훈련 시작"), Component.empty()))
        playerData.entityStatus.canAttack = true
        playerData.entityStatus.canSkillUse = true
        playerData.entityStatus.isAttackable = true

        val task = object : BukkitRunnable() {
            override fun run() {
                Targeting.synchronizeTraining(playerData)
                if (!playerData.initGame.isPaused) AbilityTree.handlers(playerData.gameClasses, GameStatusHandler::class.java)
                    .forEach { bound -> bound.call { it.onGameTimePasses() } }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L)
        trainingGame.track(task)
    }

    /** 개인 훈련 자원을 정리하고 시작 전 플레이어 상태를 복원한다. */
    fun Player.stopTraining() {
        val trainingGame = trainingInstance.find { it.activePlayers().any { data -> data.player == this } } ?: return
        trainingGame.activePlayers().forEach { data ->
            AbilityTree.end(data.gameClasses.filter { it.isInjectedFor(data) }, EndReason.GAME_END)
        }
        Contractor.clearSessions(listOf(uniqueId))
        DeathNote.clearSessions(listOf(uniqueId))
        Hacker.clearSessions(listOf(uniqueId))
        Mathematician.clearSessions(listOf(uniqueId))
        Referee.clearSessions(listOf(uniqueId))
        Vampire.clearForms(listOf(uniqueId))
        PortalGun.clearForPlayers(listOf(uniqueId))
        AreaDevelopment.clearDomains(listOf(uniqueId))
        trainingGame.tasks.toList().forEach { it.cancel() }
        TemporaryDisplayManager.clear(world, uniqueId)
        trainingGame.playerDatas.forEach { entityData ->
            (entityData as? PlayerData)?.let(StealthVisibilityManager::reveal)
            unregisterAllTickingStatuses(entityData.statusAbnormalitys)
            entityData.statusAbnormalitys.clear()
            entityData.bukkitTasks.toList().forEach { it.cancel() }
            entityData.bukkitTasks.clear()
        }
        clearDamageInvincibility(listOf(uniqueId))
        CombatManager.clear(listOf(uniqueId))
        DamageManager.clearAttributions(trainingGame.playerDatas.map { it.entity.uniqueId })
        CooldownManager.clear(listOf(uniqueId))
        DamageIndicatorManager.clearForPlayers(listOf(uniqueId))
        clearGameGlowing()
        trainingGame.playerSnapshots.remove(uniqueId)?.let { restorePlayerAfterGame(this, it) }
        trainingInstance.remove(trainingGame)
    }

    /** 플러그인 종료 등을 위해 모든 개인 훈련 경기를 일괄 정리한다. */
    fun stopAllTraining() {
        val trainingPlayerIds = trainingInstance.flatMap { it.activePlayers() }.map { it.uniqueId }
        Contractor.clearSessions(trainingPlayerIds)
        DeathNote.clearSessions(trainingPlayerIds)
        CombatManager.clear(trainingPlayerIds)
        CooldownManager.clear(trainingPlayerIds)
        DamageIndicatorManager.clearForPlayers(trainingPlayerIds)
        trainingInstance.toList().forEach { trainingGame ->
            trainingGame.activePlayers().toList().forEach { playerData ->
                if (playerData.player.isOnline) {
                    playerData.player.stopTraining()
                } else {
                    AbilityTree.end(playerData.gameClasses.filter { it.isInjectedFor(playerData) }, EndReason.GAME_END)
                    StealthVisibilityManager.reveal(playerData)
                    TemporaryDisplayManager.clear(playerData.player.world, playerData.uniqueId)
                    unregisterAllTickingStatuses(playerData.statusAbnormalitys)
                    playerData.statusAbnormalitys.clear()
                    playerData.bukkitTasks.toList().forEach { it.cancel() }
                    playerData.bukkitTasks.clear()
                    trainingGame.playerSnapshots.remove(playerData.uniqueId)?.let { snapshot ->
                        pendingPostGameCleanup[playerData.uniqueId] = snapshot
                    }
                }
            }
            trainingGame.playerDatas.filterNot { it is PlayerData }.forEach { entityData ->
                unregisterAllTickingStatuses(entityData.statusAbnormalitys)
                entityData.statusAbnormalitys.clear()
                entityData.bukkitTasks.toList().forEach { it.cancel() }
                entityData.bukkitTasks.clear()
            }
            trainingGame.tasks.toList().forEach { it.cancel() }
            trainingGame.tasks.clear()
            trainingInstance.remove(trainingGame)
        }
    }

    private fun Game.activePlayers(): List<PlayerData> = playerDatas.filterIsInstance<PlayerData>()

    private fun Game.contenders(): List<PlayerData> = activePlayers().filter { !it.entityStatus.isDead }

    private fun Game.findParticipant(playerId: UUID): PlayerData? =
        activePlayers().find { it.uniqueId == playerId }

    private fun Game.disablePlayerInteraction(playerData: PlayerData) {
        playerData.entityStatus.canAttack = false
        playerData.entityStatus.canSkillUse = false
        playerData.entityStatus.canMove = false
        playerData.entityStatus.isAttackable = false
        playerData.entityStatus.isSkillTargeting = false
    }

    private fun Game.rebindPlayer(playerData: PlayerData, player: Player) {
        playerData.player = player
        playerData.attributeEffects.refresh()
        AbilityTree.bind(playerData.gameClasses, playerData)
        if (battleInitializedPlayers.contains(player.uniqueId) && !playerData.entityStatus.isDead) {
            AbilityTree.resume(playerData.gameClasses)
        }
        playerData.statusAbnormalitys.forEach { it.rebindEntity(playerData) }
        StealthVisibilityManager.refreshAll()
    }

    private fun Game.initializeBattlePlayer(playerData: PlayerData) {
        if (battleInitializedPlayers.contains(playerData.uniqueId)) return
        playerData.classSet()
        battleInitializedPlayers.add(playerData.uniqueId)
        playerData.player.inventory.heldItemSlot = 0
    }

    private fun restorePlayerAfterGame(player: Player, snapshot: PlayerSnapshot) {
        PlayerTagManager.clear(player)
        player.playerListName(miniMessage.deserialize(player.name))
        player.closeInventory()
        player.sendActionBar(Component.empty())
        if (player.gameMode == GameMode.SPECTATOR) {
            player.spectatorTarget = null
        }
        player.resetDyeCooldowns()
        player.inventory.clear()
        player.inventory.contents = snapshot.inventoryContents.map { it?.clone() }.toTypedArray()
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        snapshot.potionEffects.forEach { player.addPotionEffect(it) }
        snapshot.movementSpeedBase?.let { player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = it }
        snapshot.attackSpeedBase?.let { player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = it }
        snapshot.maxHealthBase?.let { player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = it }
        snapshot.jumpStrengthBase?.let { player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = it }
        snapshot.scaleBase?.let { player.getAttribute(Attribute.SCALE)?.baseValue = it }
        player.walkSpeed = snapshot.walkSpeed
        player.flySpeed = snapshot.flySpeed
        player.foodLevel = snapshot.foodLevel
        player.saturation = snapshot.saturation
        player.exhaustion = snapshot.exhaustion
        player.level = snapshot.level
        player.exp = snapshot.experience
        player.totalExperience = snapshot.totalExperience
        player.fireTicks = snapshot.fireTicks
        player.gameMode = snapshot.gameMode
        player.allowFlight = snapshot.allowFlight
        player.isFlying = snapshot.isFlying && snapshot.allowFlight
        player.setGravity(snapshot.hasGravity)
        val maximumHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: player.getPlayerMaxHealth()
        player.health = snapshot.health.coerceIn(0.01, maximumHealth)
        player.teleport(snapshot.location)
        player.clearGameGlowing()
    }

    private fun Player.clearGameGlowing() {
        isGlowing = false
        removePotionEffect(PotionEffectType.GLOWING)
    }

    private fun Game.track(task: BukkitTask) {
        tasks.add(task)
    }

    private fun Game.sendNotification(message: String) {
        activePlayers().filter { it.player.isOnline }.forEach { playerData ->
            playerData.player.playSound(
                playerData.player.location,
                Sound.BLOCK_NOTE_BLOCK_GUITAR,
                SoundCategory.MASTER,
                1.0F,
                2.0F,
            )
            playerData.player.sendMessage(miniMessage.deserialize("<gray>[!] $message"))
        }
    }

    private fun Game.broadcastClassSummary() {
        val classLines = activePlayers()
            .sortedBy { it.player.name.lowercase(Locale.ROOT) }
            .joinToString("\n") { playerData ->
                val className = playerData.gameClasses
                    .joinToString(" <dark_gray>+</dark_gray> ") { it.name }
                    .ifEmpty { "<red>배정되지 않음" }
                val kills = playerKillCounts[playerData.uniqueId] ?: 0
                "<gray>- <white>${playerData.player.name}<dark_gray>: $className " +
                    "<dark_gray>| <red>킬 <white><bold>$kills</bold>"
            }
        val summary = miniMessage.deserialize(
            "<gold><bold>[게임 종료 - 클래스 및 킬 공개]</bold>\n$classLines"
        )
        Bukkit.getOnlinePlayers().forEach { it.sendMessage(summary) }
    }

    private fun formatTime(seconds: Int): String =
        String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
