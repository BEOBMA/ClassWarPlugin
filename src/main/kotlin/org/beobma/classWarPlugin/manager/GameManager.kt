package org.beobma.classWarPlugin.manager

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.game.PlayerSnapshot
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.list.*
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.InventoryManager.openAssignedClassInventory
import org.beobma.classWarPlugin.manager.PlayerManager.classSet
import org.beobma.classWarPlugin.manager.PlayerManager.clearDamageInvincibility
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.unregisterAllTickingStatuses
import org.beobma.classWarPlugin.manager.UtilManager.getPlayerMaxHealth
import org.beobma.classWarPlugin.manager.UtilManager.resetDyeCooldowns
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Gate
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Locale
import java.util.UUID
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object GameManager {
    private val miniMessage = MiniMessage.miniMessage()
    private const val RECONNECT_GRACE_TICKS = 5L * 60L * 20L
    private const val SPAWN_ESCAPE_DISTANCE = 16
    private const val SPAWN_ESCAPE_MAX_VISITED_NODES = 2_500
    private const val FINAL_BORDER_DESCENT_SECONDS = 180
    private const val FINAL_BORDER_DISPLAY_TILE_SIZE = 4.0
    private const val FINAL_BORDER_MAX_TILES_PER_AXIS = 20
    private const val FINAL_BORDER_UPDATE_INTERVAL_TICKS = 2L
    private val pendingPostGameCleanup: MutableMap<UUID, PlayerSnapshot> = mutableMapOf()

    private data class SpawnPathNode(val x: Int, val y: Int, val z: Int)

    private val gameClassFactories: List<() -> GameClass> = listOf(
        ::Berserker, ::Sniper, ::Meteor, ::TimeManiqulator, ::LandWizard,
        ::Gambler, ::Knight, ::LightningWizard, ::LightWizard,
        ::AbyssalVeil, ::Warlock, ::Geometer,
        ::Duelist, ::Astronomer, ::Assassin, ::IceWizard,
        ::GunBlader, ::Watchmaker,
        ::Barrier, ::Darkness, ::Feather, ::GeneralPerson,
        ::GraveRobber, ::Hacker, ::SpiderMan, ::Trapper,
        ::Mathematician, ::PortalGun, ::Tour, ::Pacifist, ::Roulette,
        ::AreaDevelopment, ::Parasite, ::Chubby, ::Vampire,
        ::Contractor, ::Levatain, ::WeaponMaster, ::DeathNote, ::Swordplay, ::Referee,
    )

    private val miniMessageTagPattern = Regex("<[^>]+>")

    val gameClassList: List<GameClass>
        get() = gameClassFactories.map { it() }
            .sortedWith(
                compareBy<GameClass> { it.rank.ordinal }
                    .thenBy { it.name.replace(miniMessageTagPattern, "") }
                    .thenBy { it.javaClass.simpleName }
            )

    val gameWorld: World
        get() = Bukkit.getWorlds().first()

    val trainingInstance: MutableList<Game> = mutableListOf()

    fun Game.start() {
        val participants = activePlayers()
        if (participants.size > gameClassFactories.size) {
            sendNotification("사용 가능한 클래스 수보다 참가자가 많아 게임을 시작할 수 없습니다.")
            return
        }

        game = this
        phase = GamePhase.CLASS_SELECTION
        PlayerListManager.hideAll()
        NameTagManager.hideAll(participants.map { it.player.name })
        availableClasses.clear()
        availableClasses.addAll(gameClassFactories.map { it() })
        confirmedPlayers.clear()
        refreshesRemaining.clear()
        playerKillCounts.clear()

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
            playerData.gameClass = drawRandomClass() ?: return@forEach
        }

        if (participants.any { it.gameClass == null }) {
            sendNotification("클래스 배정에 실패하여 게임을 종료합니다.")
            stop()
            return
        }

        sendNotification("무작위 클래스가 배정되었습니다. 클래스를 확인하고 확정해 주세요.")
        participants.forEach { it.openAssignedClassInventory() }
    }

    fun PlayerData.refreshAssignedClass() {
        val currentGame = initGame
        if (currentGame.phase != GamePhase.CLASS_SELECTION) return
        if (currentGame.confirmedPlayers.contains(player.uniqueId)) return

        val remaining = currentGame.refreshesRemaining[player.uniqueId] ?: 0
        if (remaining <= 0) {
            player.sendMessage(miniMessage.deserialize("<red><bold>[!] 남은 새로고침 횟수가 없습니다."))
            return
        }

        val previous = gameClass ?: return
        currentGame.availableClasses.add(previous)
        val replacement = currentGame.drawRandomClass(previous::class.java)
        if (replacement == null) {
            currentGame.availableClasses.remove(previous)
            player.sendMessage(miniMessage.deserialize("<red><bold>[!] 새로 배정할 수 있는 클래스가 없습니다."))
            return
        }

        gameClass = replacement
        currentGame.refreshesRemaining[player.uniqueId] = remaining - 1
        player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0F, 1.2F)
        openAssignedClassInventory()
    }

    fun PlayerData.confirmAssignedClass() {
        val currentGame = initGame
        if (currentGame.phase != GamePhase.CLASS_SELECTION) return
        if (!currentGame.confirmedPlayers.add(player.uniqueId)) return

        PlayerTagManager.removeTag(player, "openAssignedClassInventory")
        player.closeInventory()
        if (gameClass == null) return
        player.playerListName(miniMessage.deserialize(player.name))
        currentGame.sendNotification("${player.name}님이 클래스를 확정했습니다. (${currentGame.confirmedPlayers.size}/${currentGame.contenders().size})")

        if (currentGame.contenders().all { currentGame.confirmedPlayers.contains(it.player.uniqueId) }) {
            currentGame.beginCountdown()
        }
    }

    private fun Game.drawRandomClass(excludedType: Class<out GameClass>? = null): GameClass? {
        val candidates = if (excludedType == null) {
            availableClasses
        } else {
            availableClasses.filter { it.javaClass != excludedType }
        }
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

    private fun Game.beginCountdown() {
        phase = GamePhase.COUNTDOWN
        val participants = contenders()
        participants.forEach {
            PlayerTagManager.removeTag(it.player, "openAssignedClassInventory")
            it.player.closeInventory()
            it.entityStatus.canMove = false
        }

        selectRandomRoundCenter()
        val spawnPoints = findSpawnLocations(gameWorld, participants.size)
        if (spawnPoints == null) {
            sendNotification("설정된 범위에서 안전한 시작 지점을 충분히 찾지 못했습니다. 산개 설정을 확인해 주세요.")
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
        if (spawnLocations.size < participants.size) {
            sendNotification("산개 위치가 부족하여 게임을 종료합니다.")
            stop()
            return
        }

        assignedSpawnLocations.clear()
        participants.zip(spawnLocations.shuffled()).forEach { (playerData, location) ->
            val playerId = playerData.player.uniqueId
            assignedSpawnLocations[playerId] = location.clone()
            if (!playerData.player.isOnline) return@forEach
            playerData.player.teleport(location)
            initializeBattlePlayer(playerData)
        }
        beginBattle()
    }

    private fun Game.beginBattle() {
        phase = GamePhase.RUNNING
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
                    miniMessage.deserialize("<gray>마지막 생존자가 되세요.")
                )
            )
        }
        sendNotification("게임이 시작되었습니다.")
        startClassTickTask()
        startWorldBorder()
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
                    playerData.gameClass?.passives?.filterIsInstance<GameStatusHandler>()
                        ?.forEach { it.onGameTimePasses() }
                    (playerData.gameClass as? GameStatusHandler)?.onGameTimePasses()
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

                if (isPaused) {
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
                    val remaining = ((remainingTicks + 19L) / 20L).toInt()
                    bossBar.name(miniMessage.deserialize("<aqua><bold>월드보더 축소까지 ${formatTime(remaining)}"))
                    bossBar.progress((remainingTicks.toFloat() / total).coerceIn(0.0F, 1.0F))
                } else {
                    val progress = (elapsedTicks.toDouble() / shrinkTicks).coerceIn(0.0, 1.0)
                    border.setCenter(
                        shrinkStartX + (shrinkTargetX - shrinkStartX) * progress,
                        shrinkStartZ + (shrinkTargetZ - shrinkStartZ) * progress,
                    )
                    val remainingTicks = (shrinkTicks - elapsedTicks).coerceAtLeast(0L)
                    val remaining = ((remainingTicks + 19L) / 20L).toInt()
                    bossBar.name(miniMessage.deserialize("<red><bold>월드보더 축소 중 ${formatTime(remaining)}"))
                    bossBar.progress((remainingTicks.toFloat() / shrinkTicks).coerceIn(0.0F, 1.0F))
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
        val coveredSize = borderSize.coerceAtLeast(1.0)
        val tilesPerAxis = ceil(coveredSize / FINAL_BORDER_DISPLAY_TILE_SIZE).toInt()
            .coerceIn(1, FINAL_BORDER_MAX_TILES_PER_AXIS)
        val tileSpan = coveredSize / tilesPerAxis
        val visibleTileSpan = (tileSpan * 0.965).toFloat()
        val tileInset = ((tileSpan - visibleTileSpan) * 0.5).toFloat()
        val startY = (world.maxHeight - 1).toDouble()
        val endY = (world.minHeight + 1).toDouble()
        val minimumX = centerX - coveredSize * 0.5
        val minimumZ = centerZ - coveredSize * 0.5

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
        bossBar.name(miniMessage.deserialize("<dark_red><bold>상공 자기장 하강 중 ${formatTime(FINAL_BORDER_DESCENT_SECONDS)}"))
        sendNotification("<dark_red><bold>최종 자기장이 상공에서 하강하기 시작합니다.")
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

        val totalTicks = FINAL_BORDER_DESCENT_SECONDS * 20L
        var elapsedTicks = 0L
        val descentTask = object : BukkitRunnable() {
            override fun run() {
                if (phase != GamePhase.RUNNING) {
                    clearFinalBorderDisplays()
                    activePlayers().filter { it.player.isOnline }.forEach { it.player.hideBossBar(bossBar) }
                    if (borderBossBar === bossBar) borderBossBar = null
                    cancel()
                    return
                }
                if (isPaused) return

                val progress = (elapsedTicks.toDouble() / totalTicks).coerceIn(0.0, 1.0)
                val currentY = startY + (endY - startY) * progress
                finalBorderDisplays.toList().forEach { display ->
                    if (!display.isValid) {
                        finalBorderDisplays.remove(display)
                        return@forEach
                    }
                    display.teleport(display.location.apply { y = currentY })
                }

                val remainingTicks = (totalTicks - elapsedTicks).coerceAtLeast(0L)
                bossBar.progress((1.0 - progress).toFloat().coerceIn(0.0F, 1.0F))
                if (elapsedTicks % 20L == 0L) {
                    val remainingSeconds = ((remainingTicks + 19L) / 20L).toInt()
                    bossBar.name(miniMessage.deserialize(
                        "<dark_red><bold>상공 자기장 하강 중 ${formatTime(remainingSeconds)}"
                    ))
                }

                if (elapsedTicks >= totalTicks) {
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
                    cancel()
                    return
                }
                elapsedTicks = (elapsedTicks + FINAL_BORDER_UPDATE_INTERVAL_TICKS).coerceAtMost(totalTicks)
            }
        }.runTaskTimer(
            ClassWarPlugin.instance,
            0L,
            FINAL_BORDER_UPDATE_INTERVAL_TICKS,
        )
        track(descentTask)
    }

    private fun Game.clearFinalBorderDisplays() {
        finalBorderDisplays.toList().forEach { display ->
            if (display.isValid) display.remove()
        }
        finalBorderDisplays.clear()
    }

    private fun Game.selectRandomRoundCenter() {
        roundCenterX = settings.centerX
        roundCenterZ = settings.centerZ
        if (!settings.borderEnabled) return

        val minimumOffset = minOf(settings.borderCenterMinimumDistance, settings.borderCenterMaximumDistance)
        val maximumOffset = maxOf(settings.borderCenterMinimumDistance, settings.borderCenterMaximumDistance)
        if (maximumOffset == 0.0) return

        val angle = Random.nextDouble(0.0, PI * 2.0)
        val radius = if (minimumOffset < maximumOffset) {
            sqrt(Random.nextDouble(minimumOffset * minimumOffset, maximumOffset * maximumOffset))
        } else {
            minimumOffset
        }
        roundCenterX += cos(angle) * radius
        roundCenterZ += sin(angle) * radius
    }

    private fun Game.findSpawnLocations(world: World, count: Int): List<Location>? {
        val result = mutableListOf<Location>()
        repeat(count) {
            var selected: Location? = null
            repeat(750) {
                if (selected != null) return@repeat
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
                val candidate = safeSurfaceLocation(world, x, z) ?: return@repeat
                if (result.any { it.distanceSquared(candidate) < settings.minimumPlayerDistance * settings.minimumPlayerDistance }) {
                    return@repeat
                }
                if (!hasSpawnEscapeRoute(world, candidate)) return@repeat
                selected = candidate
            }
            result.add(selected ?: return null)
        }
        return result
    }

    private fun safeSurfaceLocation(world: World, x: Int, z: Int): Location? {
        world.getChunkAt(x shr 4, z shr 4).load()
        val y = world.getHighestBlockYAt(x, z)
        if (y - 3 < world.minHeight || y + 2 >= world.maxHeight) return null
        val surface = world.getBlockAt(x, y, z)
        if (!isNaturalGround(surface)) return null
        if (!world.getBlockAt(x, y + 1, z).type.isAir || !world.getBlockAt(x, y + 2, z).type.isAir) return null
        if ((1..3).any { depth -> !isNaturalGround(world.getBlockAt(x, y - depth, z)) }) return null
        return Location(world, x + 0.5, y + 1.0, z + 0.5)
    }

    private fun hasSpawnEscapeRoute(world: World, spawn: Location): Boolean {
        val start = SpawnPathNode(spawn.blockX, spawn.blockY, spawn.blockZ)
        if (!isSafeStandingNode(world, start)) return false

        val queue = ArrayDeque<SpawnPathNode>()
        val visited = HashSet<SpawnPathNode>()
        queue.add(start)
        visited.add(start)
        val requiredDistanceSquared = SPAWN_ESCAPE_DISTANCE * SPAWN_ESCAPE_DISTANCE
        val directions = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(-1, 0),
            intArrayOf(0, 1),
            intArrayOf(0, -1),
        )

        while (queue.isNotEmpty() && visited.size <= SPAWN_ESCAPE_MAX_VISITED_NODES) {
            val current = queue.removeFirst()
            val deltaX = current.x - start.x
            val deltaZ = current.z - start.z
            if (deltaX * deltaX + deltaZ * deltaZ >= requiredDistanceSquared && isOpenOutdoorNode(world, current)) {
                return true
            }

            directions.forEach { direction ->
                val nextX = current.x + direction[0]
                val nextZ = current.z + direction[1]
                val horizontalX = nextX - start.x
                val horizontalZ = nextZ - start.z
                if (horizontalX * horizontalX + horizontalZ * horizontalZ > requiredDistanceSquared + SPAWN_ESCAPE_DISTANCE) {
                    return@forEach
                }
                val nextY = findReachableStandingY(world, current, nextX, nextZ) ?: return@forEach
                val next = SpawnPathNode(nextX, nextY, nextZ)
                if (visited.add(next)) queue.addLast(next)
            }
        }
        return false
    }

    private fun findReachableStandingY(world: World, current: SpawnPathNode, x: Int, z: Int): Int? {
        for (verticalOffset in intArrayOf(1, 0, -1, -2, -3)) {
            val candidateY = current.y + verticalOffset
            val candidate = SpawnPathNode(x, candidateY, z)
            if (!isSafeStandingNode(world, candidate)) continue
            if (verticalOffset > 0 && !isTraversable(world.getBlockAt(current.x, current.y + 2, current.z))) continue
            return candidateY
        }
        return null
    }

    private fun isSafeStandingNode(world: World, node: SpawnPathNode): Boolean {
        if (node.y - 1 < world.minHeight || node.y + 1 >= world.maxHeight) return false
        val feet = world.getBlockAt(node.x, node.y, node.z)
        val head = world.getBlockAt(node.x, node.y + 1, node.z)
        val ground = world.getBlockAt(node.x, node.y - 1, node.z)
        if (!isTraversable(feet) || !isTraversable(head) || !ground.type.isSolid) return false
        return feet.type !in unsafeSpawnPathMaterials &&
            head.type !in unsafeSpawnPathMaterials &&
            ground.type !in unsafeSpawnPathMaterials
    }

    private fun isOpenOutdoorNode(world: World, node: SpawnPathNode): Boolean {
        if ((0..2).any { offset -> !isTraversable(world.getBlockAt(node.x, node.y + offset, node.z)) }) return false
        return world.getHighestBlockYAt(node.x, node.z) <= node.y - 1
    }

    private fun isTraversable(block: Block): Boolean {
        if (block.isPassable) return true
        return when (val data = block.blockData) {
            is Door -> block.type != Material.IRON_DOOR
            is TrapDoor -> block.type != Material.IRON_TRAPDOOR
            is Gate -> true
            else -> false
        }
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

    private val unsafeSpawnPathMaterials = setOf(
        Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW,
        Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE,
    )

    fun handleDeath(playerData: PlayerData) {
        val currentGame = playerData.initGame
        if (currentGame.phase != GamePhase.RUNNING || playerData.entityStatus.isDead) return
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
        playerData.bukkitTasks.forEach { it.cancel() }
        playerData.bukkitTasks.clear()
        playerData.player.gameMode = GameMode.SPECTATOR

        val survivors = currentGame.contenders()
        if (survivors.size <= 1) currentGame.finish(survivors.firstOrNull())
    }

    fun Game.recordPlayerKill(victimId: UUID, killerId: UUID?) {
        val creditedKillerId = killerId?.takeIf { it != victimId } ?: return
        if (phase != GamePhase.RUNNING) return
        if (activePlayers().none { it.uniqueId == creditedKillerId }) return
        playerKillCounts[creditedKillerId] = (playerKillCounts[creditedKillerId] ?: 0) + 1
    }

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
                    currentGame.assignedSpawnLocations[player.uniqueId]?.let { player.teleport(it) }
                    currentGame.initializeBattlePlayer(playerData)
                }
                playerData.entityStatus.canAttack = true
                playerData.entityStatus.canSkillUse = true
                playerData.entityStatus.canMove = true
                playerData.entityStatus.isAttackable = true
                playerData.entityStatus.isSkillTargeting = true
                player.gameMode = GameMode.ADVENTURE
                currentGame.borderBossBar?.let { player.showBossBar(it) }
                player.sendMessage(miniMessage.deserialize("<green><bold>[!] 게임에 정상적으로 복귀했습니다."))
            }

            GamePhase.WAITING, GamePhase.FINISHED -> Unit
        }
    }

    fun refreshPlayerListVisibility() {
        val currentGame = game ?: return
        PlayerListManager.hideAll()
        NameTagManager.hideAll(currentGame.activePlayers().map { it.player.name })
    }

    private fun Game.permanentlyEliminateDisconnectedPlayer(playerData: PlayerData) {
        playerData.entityStatus.isDead = true
        StealthVisibilityManager.reveal(playerData)
        expiredReconnectPlayers.add(playerData.uniqueId)
        disablePlayerInteraction(playerData)
        playerData.bukkitTasks.toList().forEach { it.cancel() }
        playerData.bukkitTasks.clear()
        confirmedPlayers.remove(playerData.uniqueId)
        if (phase == GamePhase.CLASS_SELECTION) {
            playerData.gameClass?.let { assigned ->
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

    fun Game.stop() {
        val wasRunning = phase == GamePhase.RUNNING
        phase = GamePhase.FINISHED
        if (wasRunning) broadcastClassSummary()
        val participantIds = activePlayers().map { it.uniqueId }
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

    fun findGameForPlayer(player: Player): Game? =
        if (PlayerTagManager.hasTag(player, "isTraining")) {
            trainingInstance.find { instance -> instance.activePlayers().any { it.player == player } }
        } else {
            game
        }

    fun Player.startTraining(gameClass: GameClass) {
        val trainingGame = Game(mutableListOf())
        val playerData = PlayerData(this, trainingGame)
        trainingGame.playerSnapshots[uniqueId] = PlayerSnapshot.capture(this)
        trainingGame.playerDatas.add(playerData)
        playerData.gameClass = gameClass
        trainingInstance.add(trainingGame)
        PlayerTagManager.addTag(this, "isTraining")
        playerData.classSet()
        inventory.heldItemSlot = 0
        showTitle(Title.title(miniMessage.deserialize("<bold>훈련 시작"), Component.empty()))
        playerData.entityStatus.canAttack = true
        playerData.entityStatus.canSkillUse = true
        playerData.entityStatus.isAttackable = true

        val task = object : BukkitRunnable() {
            override fun run() {
                playerData.gameClass?.passives?.filterIsInstance<GameStatusHandler>()
                    ?.forEach { it.onGameTimePasses() }
                (playerData.gameClass as? GameStatusHandler)?.onGameTimePasses()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 20L, 20L)
        trainingGame.track(task)
    }

    fun Player.stopTraining() {
        val trainingGame = trainingInstance.find { it.activePlayers().any { data -> data.player == this } } ?: return
        Contractor.clearSessions(listOf(uniqueId))
        DeathNote.clearSessions(listOf(uniqueId))
        Hacker.clearSessions(listOf(uniqueId))
        Mathematician.clearSessions(listOf(uniqueId))
        Referee.clearSessions(listOf(uniqueId))
        Vampire.clearForms(listOf(uniqueId))
        PortalGun.clearForPlayers(listOf(uniqueId))
        AreaDevelopment.clearDomains(listOf(uniqueId))
        trainingGame.tasks.forEach { it.cancel() }
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
        trainingGame.playerSnapshots.remove(uniqueId)?.let { restorePlayerAfterGame(this, it) }
        trainingInstance.remove(trainingGame)
    }

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
        playerData.gameClass?.let { gameClass ->
            gameClass.inject(playerData)
            gameClass.skills.forEach { it.inject(playerData) }
            gameClass.passives.forEach { it.inject(playerData) }
        }
        playerData.statusAbnormalitys.forEach { it.rebindEntity(playerData) }
        StealthVisibilityManager.refreshAll()
    }

    private fun Game.initializeBattlePlayer(playerData: PlayerData) {
        if (!battleInitializedPlayers.add(playerData.uniqueId)) return
        playerData.classSet()
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
                val className = playerData.gameClass?.name ?: "<red>배정되지 않음"
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
