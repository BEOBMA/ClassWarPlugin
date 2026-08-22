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
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object GameManager {
    private val miniMessage = MiniMessage.miniMessage()
    private const val RECONNECT_GRACE_TICKS = 5L * 60L * 20L
    private val pendingPostGameCleanup: MutableMap<UUID, PlayerSnapshot> = mutableMapOf()

    private val gameClassFactories: List<() -> GameClass> = listOf(
        ::Berserker, ::Sniper, ::Meteor, ::TimeManiqulator, ::LandWizard,
        ::Gambler, ::Knight, ::LightningWizard, ::LightWizard,
        ::AbyssalVeil, ::Warlock, ::Mathematician,
        ::Duelist, ::Astronomer, ::Assassin, ::IceWizard,
        ::GunBlader, ::Watchmaker,
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
        availableClasses.clear()
        availableClasses.addAll(gameClassFactories.map { it() })
        confirmedPlayers.clear()
        refreshesRemaining.clear()

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
        player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.2F)
        openAssignedClassInventory()
    }

    fun PlayerData.confirmAssignedClass() {
        val currentGame = initGame
        if (currentGame.phase != GamePhase.CLASS_SELECTION) return
        if (!currentGame.confirmedPlayers.add(player.uniqueId)) return

        PlayerTagManager.removeTag(player, "openAssignedClassInventory")
        player.closeInventory()
        val assignedClass = gameClass ?: return
        player.playerListName(miniMessage.deserialize("${player.name} <gray>[ ${assignedClass.name} <gray>]"))
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
                    playerData.player.playSound(playerData.player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.0F)
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
            playerData.player.gameMode = GameMode.SURVIVAL
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
        border.setCenter(settings.centerX, settings.centerZ)
        border.size = settings.borderInitialSize

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
        var shrinkStartX = settings.centerX
        var shrinkStartZ = settings.centerZ
        var shrinkTargetX = settings.centerX
        var shrinkTargetZ = settings.centerZ
        val task = object : BukkitRunnable() {
            override fun run() {
                if (phase != GamePhase.RUNNING) {
                    cancel()
                    return
                }

                if (!shrinking && elapsedTicks >= delayTicks) {
                    shrinking = true
                    elapsedTicks = 0L
                    shrinkStartX = border.center.x
                    shrinkStartZ = border.center.z
                    val maximumOffset = ((border.size - settings.borderMinimumSize) / 2.0).coerceAtLeast(0.0)
                    shrinkTargetX = shrinkStartX + Random.nextDouble(-maximumOffset, maximumOffset)
                    shrinkTargetZ = shrinkStartZ + Random.nextDouble(-maximumOffset, maximumOffset)
                    border.changeSize(settings.borderMinimumSize, settings.borderShrinkSeconds.toLong() * 20L)
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
                        activePlayers().filter { it.player.isOnline }.forEach { it.player.hideBossBar(bossBar) }
                        borderBossBar = null
                        cancel()
                        return
                    }
                }
                elapsedTicks++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
        track(task)
    }

    private fun Game.findSpawnLocations(world: World, count: Int): List<Location>? {
        val result = mutableListOf<Location>()
        repeat(count) {
            var selected: Location? = null
            repeat(750) {
                if (selected != null) return@repeat
                val angle = Random.nextDouble(0.0, PI * 2.0)
                val minSquared = settings.scatterMinRadius * settings.scatterMinRadius
                val maxSquared = settings.scatterMaxRadius * settings.scatterMaxRadius
                val radius = sqrt(Random.nextDouble(minSquared, maxSquared))
                val x = floor(settings.centerX + cos(angle) * radius).toInt()
                val z = floor(settings.centerZ + sin(angle) * radius).toInt()
                val candidate = safeSurfaceLocation(world, x, z) ?: return@repeat
                if (result.any { it.distanceSquared(candidate) < settings.minimumPlayerDistance * settings.minimumPlayerDistance }) {
                    return@repeat
                }
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
        playerData.bukkitTasks.forEach { it.cancel() }
        playerData.bukkitTasks.clear()
        playerData.player.gameMode = GameMode.SPECTATOR

        val survivors = currentGame.contenders()
        if (survivors.size <= 1) currentGame.finish(survivors.firstOrNull())
    }

    fun handleTemporaryDisconnect(player: Player) {
        val currentGame = game ?: return
        if (currentGame.phase == GamePhase.WAITING || currentGame.phase == GamePhase.FINISHED) return
        val playerData = currentGame.findParticipant(player.uniqueId) ?: return
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

        val assignedClass = playerData.gameClass
        if (assignedClass != null) {
            player.playerListName(miniMessage.deserialize("${player.name} <gray>[ ${assignedClass.name} <gray>]"))
        }

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
                player.gameMode = GameMode.SURVIVAL
                currentGame.borderBossBar?.let { player.showBossBar(it) }
                player.sendMessage(miniMessage.deserialize("<green><bold>[!] 게임에 정상적으로 복귀했습니다."))
            }

            GamePhase.WAITING, GamePhase.FINISHED -> Unit
        }
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
        val task = object : BukkitRunnable() {
            override fun run() = stop()
        }.runTaskLater(ClassWarPlugin.instance, 100L)
        track(task)
    }

    fun Game.stop() {
        phase = GamePhase.FINISHED
        val participantIds = activePlayers().map { it.uniqueId }
        DamageManager.clearAttributions(participantIds)
        clearDamageInvincibility(participantIds)
        CooldownManager.clear(participantIds)
        DamageIndicatorManager.clearForPlayers(participantIds)
        disconnectTasks.values.forEach { it.cancel() }
        disconnectTasks.clear()
        tasks.toList().forEach { it.cancel() }
        tasks.clear()
        borderBossBar?.let { bar -> activePlayers().filter { it.player.isOnline }.forEach { it.player.hideBossBar(bar) } }
        borderBossBar = null
        originalBorderCenter?.let { gameWorld.worldBorder.setCenter(it.x, it.z) }
        originalBorderSize?.let { gameWorld.worldBorder.size = it }

        activePlayers().forEach { playerData ->
            StealthVisibilityManager.reveal(playerData)
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
        playerSnapshots.clear()
        disconnectedPlayers.clear()
        expiredReconnectPlayers.clear()
        assignedSpawnLocations.clear()
        battleInitializedPlayers.clear()
        availableClasses.clear()
        refreshesRemaining.clear()
        confirmedPlayers.clear()
        spawnLocations.clear()
        originalBorderCenter = null
        originalBorderSize = null
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
        trainingGame.tasks.forEach { it.cancel() }
        trainingGame.activePlayers().forEach { playerData ->
            StealthVisibilityManager.reveal(playerData)
            unregisterAllTickingStatuses(playerData.statusAbnormalitys)
            playerData.statusAbnormalitys.clear()
            playerData.bukkitTasks.toList().forEach { it.cancel() }
            playerData.bukkitTasks.clear()
        }
        clearDamageInvincibility(listOf(uniqueId))
        DamageManager.clearAttributions(listOf(uniqueId))
        CooldownManager.clear(listOf(uniqueId))
        DamageIndicatorManager.clearForPlayers(listOf(uniqueId))
        trainingGame.playerSnapshots.remove(uniqueId)?.let { restorePlayerAfterGame(this, it) }
        trainingInstance.remove(trainingGame)
    }

    fun stopAllTraining() {
        val trainingPlayerIds = trainingInstance.flatMap { it.activePlayers() }.map { it.uniqueId }
        CooldownManager.clear(trainingPlayerIds)
        DamageIndicatorManager.clearForPlayers(trainingPlayerIds)
        trainingInstance.toList().forEach { trainingGame ->
            trainingGame.activePlayers().toList().forEach { playerData ->
                if (playerData.player.isOnline) {
                    playerData.player.stopTraining()
                } else {
                    StealthVisibilityManager.reveal(playerData)
                    unregisterAllTickingStatuses(playerData.statusAbnormalitys)
                    playerData.statusAbnormalitys.clear()
                    playerData.bukkitTasks.toList().forEach { it.cancel() }
                    playerData.bukkitTasks.clear()
                    trainingGame.playerSnapshots.remove(playerData.uniqueId)?.let { snapshot ->
                        pendingPostGameCleanup[playerData.uniqueId] = snapshot
                    }
                }
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
        val maximumHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: player.getPlayerMaxHealth()
        player.health = snapshot.health.coerceIn(0.01, maximumHealth)
        player.teleport(snapshot.location)
    }

    private fun Game.track(task: BukkitTask) {
        tasks.add(task)
    }

    private fun Game.sendNotification(message: String) {
        activePlayers().filter { it.player.isOnline }.forEach { playerData ->
            playerData.player.playSound(playerData.player.location, Sound.BLOCK_NOTE_BLOCK_GUITAR, 1.0F, 2.0F)
            playerData.player.sendMessage(miniMessage.deserialize("<gray>[!] $message"))
        }
    }

    private fun formatTime(seconds: Int): String =
        String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
