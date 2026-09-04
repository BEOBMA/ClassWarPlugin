package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ability.TickPolicy

import org.beobma.classWarPlugin.ability.Control
import org.beobma.classWarPlugin.ability.ControlLease

import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.effect.EffectSuppressionManager
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.MapTransferBorderManager
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.miniMessage
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.Openable
import org.bukkit.block.data.type.Door
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import java.time.Duration
import java.util.UUID

private const val HIDE_AND_SEEK_COOLDOWN_SECONDS = 3000

class HideAndSeek : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler {
    override val classId = "hide-and-seek"
    override val name = "<gray>숨바꼭질"
    override val rank = Rank.SPECIAL
    override val classItemMaterial = Material.SPRUCE_DOOR
    private val hideAndSeekSkill = RedSkill()
    override var skills: List<Skill> = listOf(hideAndSeekSkill)
    override var passives: List<BasePassive> = listOf(Passive())
    private var finalPlayTriggered = false

    override fun onBattleStart() {
        finalPlayTriggered = false
    }

    override fun onGameTimePasses() {
        if (finalPlayTriggered || activeSession != null) return
        val survivors = game.playerDatas.filterIsInstance<PlayerData>()
            .filter { !it.entityStatus.isDead && it.player.isOnline }
        if (survivors.size != 2 || playerData !in survivors) return
        val target = survivors.first { it != playerData }
        finalPlayTriggered = true
        startSession(this, target, moveDeadSpectatorsOnce = true)
    }

    override fun onGameEnd() = endForGame(game)
    override fun onPlayerDeath() = endForPlayer(player.uniqueId)

    private inner class RedSkill : Skill() {
        override val definitionId = "hide-and-seek/red-skill"
        override val name = "<bold>하이드 앤 시크"
        override val description = listOf(
            "<gray>10칸 내의 바라보는 플레이어와 숨바꼭질을 시작한다.",
            "<gray>자신과 지정한 상대만 숨바꼭질 맵으로 이동한다.",
            "<gray>다른 플레이어는 원래 위치에 남으며 술래의 시점으로 강제 관전하지 않는다.", "",
            "<gray>자신은 술래가 되고, 상대가 숨을 때까지 행동할 수 없다.",
            "<gray>상대가 숨은 후, 자신은 총 4개의 방이 있는 맵을 돌아니며 상대를 찾아야 한다.",
            "<gray>각 방에는 4개의 문이 있으며, 자신이 열 수 있는 문은 4개로 한정된다.",
            "<gray>문을 열 때마다 사람이 없는 무작위 방 하나의 불이 꺼진다.",
            "<gray>문 열기 기회를 모두 소모하면 모든 문이 열리고 상대에게 대적 가능한 무기가 주어진다.",
            "<gray>숨바꼭질 도중 서로가 무기로 피해를 입히면 상대방을 즉시 {keyword:Execution}시킨다.", "",
            "<dark_gray>종료 후 자신과 상대는 원래 위치로 복귀한다.",
            "<dark_gray>숨바꼭질 중 월드보더, 게임 타이머, 쿨타임과 상태이상 시간이 정지한다.",
            "<dark_gray>숨바꼭질 중 클래스 파티클, 사운드와 표시 엔티티가 보이지 않는다.",
            "<dark_gray>상대가 숨는 동안 술래에게는 모든 소리가 들리지 않는다.",
            "<dark_gray>자신과 상대방은 숨바꼭질 중 모든 아이템을 소실한다. (복귀 후 돌려받는다.)"
        )
        override val cooldown = HIDE_AND_SEEK_COOLDOWN_SECONDS
        private var selectedTarget: PlayerData? by requestValue { null }

        override fun isUseSuccess(): Boolean {
            if (activeSession != null) {
                player.sendMiniMessage("<red><bold>[!] 이미 숨바꼭질이 진행 중입니다.")
                return false
            }
            selectedTarget = playerData.shotLaserGetEntityData(10.0, TargetType.Enemy, false) as? PlayerData
            if (selectedTarget == null && PlayerTagManager.isTraining(player)) {
                selectedTarget = playerData
                player.sendMiniMessage("<gold><bold>[모의 숨바꼭질]</bold> <gray>가상의 플레이어가 숨은 방을 탐색합니다.")
            }
            if (selectedTarget == null) player.sendMiniMessage("<red><bold>[!] 10칸 내에 바라보는 플레이어가 없습니다.")
            return selectedTarget != null
        }

        override fun use(): Boolean {
            val target = selectedTarget ?: return false
            selectedTarget = null
            startSession(this@HideAndSeek, target, moveDeadSpectatorsOnce = false)
            return true
        }
    }

    private class Passive : org.beobma.classWarPlugin.skill.Passive() {
        override val name = "<bold>마지막 놀이"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>자신을 포함하여 생존한 플레이어가 2명일 경우, 즉시 하이드 앤 시크 스킬을 사용한다.",
            "<gray>이 효과는 재사용 대기 시간을 무시한다.",
            "<gray>발동 시 사망한 관전자들은 한 번만 술래 위치로 이동하며 시점은 강제로 고정되지 않는다."
        )
    }

    companion object {
        private data class Room(val id: Int, val bounds: BoundingBox, val doors: List<Triple<Int, Int, Int>>, val lights: List<Triple<Int, Int, Int>>)
        private data class PlayerSnapshot(
            val data: PlayerData,
            val location: Location,
            val gameMode: GameMode,
            val storage: Array<ItemStack?>,
            val armor: Array<ItemStack?>,
            val extra: Array<ItemStack?>,
            val blindness: PotionEffect?,
            val glowing: Boolean,
            val fireTicks: Int,
        )

        private data class DeadSpectatorSnapshot(
            val player: Player,
            val location: Location,
            val gameMode: GameMode,
            val spectatorTarget: org.bukkit.entity.Entity?,
        )

        private val rooms = listOf(
            Room(1, BoundingBox(638.0, -32.0, -474.0, 651.0, -26.0, -467.0),
                listOf(642, 644, 646, 648).map { Triple(it, -30, -471) },
                listOf(Triple(642, -28, -472), Triple(648, -28, -472))),
            Room(2, BoundingBox(638.0, -32.0, -468.0, 651.0, -26.0, -461.0),
                listOf(642, 644, 646, 648).map { Triple(it, -30, -465) },
                listOf(Triple(642, -28, -466), Triple(648, -28, -466))),
            Room(3, BoundingBox(638.0, -32.0, -450.0, 651.0, -26.0, -443.0),
                listOf(642, 644, 646, 648).map { Triple(it, -30, -447) },
                listOf(Triple(642, -28, -446), Triple(648, -28, -446))),
            Room(4, BoundingBox(638.0, -32.0, -444.0, 651.0, -26.0, -437.0),
                listOf(642, 644, 646, 648).map { Triple(it, -30, -441) },
                listOf(Triple(642, -28, -440), Triple(648, -28, -440))),
        )
        private val corridorDoors = listOf(
            Triple(638, -30, -470), Triple(638, -30, -464), Triple(638, -30, -448),
            Triple(638, -30, -442), Triple(630, -30, -456),
        )
        private val barrierBlocks = listOf(Triple(631, -29, -456), Triple(631, -30, -456))
        private var activeSession: Session? = null

        private class Session(
            val ownerClass: HideAndSeek,
            val hider: PlayerData,
            moveDeadSpectatorsOnce: Boolean,
        ) {
            val game: Game = ownerClass.game
            val seeker: PlayerData = ownerClass.playerData
            val soloTraining = seeker == hider && PlayerTagManager.isTraining(seeker.player)
            val world = seeker.player.world
            val snapshots = game.playerDatas.filterIsInstance<PlayerData>()
                .filter { !it.entityStatus.isDead && it.player.isOnline }
                .associate { it.uniqueId to snapshot(it) }
            private val controls = snapshots.mapValues { (_, saved) -> ControlLease(ownerClass.abilityScope, saved.data.entityStatus) }
            val deadSpectatorSnapshots = if (moveDeadSpectatorsOnce) {
                game.playerDatas.filterIsInstance<PlayerData>()
                    .filter { it.entityStatus.isDead && it.player.isOnline && it.player.gameMode == GameMode.SPECTATOR }
                    .associate { data ->
                        data.uniqueId to DeadSpectatorSnapshot(
                            data.player,
                            data.player.location.clone(),
                            data.player.gameMode,
                            data.player.spectatorTarget,
                        )
                    }
            } else {
                emptyMap()
            }
            val blockSnapshots = linkedMapOf<String, BlockState>()
            val hiderOpenedDoors = mutableSetOf<Triple<Int, Int, Int>>()
            val seekerOpenedDoors = mutableSetOf<Triple<Int, Int, Int>>()
            val extinguishedRoomIds = mutableSetOf<Int>()
            var hidingComplete = false
            var hiddenDoor: Triple<Int, Int, Int>? = null
            var attempts = 4
            var found = false
            var duel = false
            var searchTicks = 0
            var task: BukkitTask? = null
            private var borderExpansion: MapTransferBorderManager.Expansion? = null
            private var effectSuppression: EffectSuppressionManager.Suppression? = null
            private var displaySuppression: TemporaryDisplayManager.VisibilitySuppression? = null

            fun start() {
                activeSession = this
                game.isPaused = true
                effectSuppression = EffectSuppressionManager.suppress(world)
                displaySuppression = TemporaryDisplayManager.suppressVisibility(
                    world,
                    game.playerDatas.map { it.entity.uniqueId },
                    snapshots.keys + deadSpectatorSnapshots.keys,
                )
                borderExpansion = MapTransferBorderManager.expandToMaximum(world)
                pauseCooldowns()
                prepareMap()
                if (soloTraining) {
                    hidingComplete = true
                    hiddenDoor = rooms.flatMap { it.doors }.random()
                    barrierBlocks.forEach { (x, y, z) -> setBlock(world.getBlockAt(x, y, z), Material.AIR) }
                }
                controls.values.forEach { it.close() }
                snapshots.values.forEach { snapshot ->
                    val data = snapshot.data
                    val participant = data == seeker || data == hider
                    controls.getValue(data.uniqueId).allow(Control.SKILL, false)
                    controls.getValue(data.uniqueId).allow(Control.ATTACK, false)
                    controls.getValue(data.uniqueId).allow(Control.ATTACKABLE, participant)
                    controls.getValue(data.uniqueId).allow(Control.TARGETABLE, participant)
                    if (!participant) {
                        controls.getValue(data.uniqueId).allow(Control.MOVE, false)
                        return@forEach
                    }
                    data.player.closeInventory()
                    data.player.stopAllSounds()
                    data.player.isGlowing = false
                    data.player.fireTicks = 0
                    data.player.inventory.clear()
                    if (data == seeker) {
                        controls.getValue(data.uniqueId).allow(Control.MOVE, soloTraining)
                        data.player.gameMode = GameMode.ADVENTURE
                        data.player.teleport(Location(world, 628.5, -30.0, -455.5, -90f, 0f))
                        giveWeapon(data.player, "<red><bold>술래의 무기")
                        if (soloTraining) {
                            controls.getValue(data.uniqueId).allow(Control.ATTACK, true)
                            data.player.showTitle(Title.title(
                                miniMessage.deserialize("<gold><bold>모의 탐색 시작!"),
                                miniMessage.deserialize("<gray>가상의 플레이어가 숨은 방을 찾으세요."),
                                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500)),
                            ))
                        }
                    } else if (data == hider) {
                        controls.getValue(data.uniqueId).allow(Control.MOVE, true)
                        data.player.gameMode = GameMode.ADVENTURE
                        data.player.teleport(Location(world, 632.5, -30.0, -455.5, -90f, 0f))
                        data.player.showTitle(Title.title(
                            miniMessage.deserialize("<red><bold>숨으세요!"),
                            miniMessage.deserialize("<gray>문을 열고 방에 들어간 뒤 다시 닫으세요."),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500)),
                        ))
                    }
                }
                deadSpectatorSnapshots.values.forEach { snapshot ->
                    snapshot.player.spectatorTarget = null
                    snapshot.player.gameMode = GameMode.SPECTATOR
                    snapshot.player.teleport(seeker.player.location)
                }
                val activationLocation = Location(world, 631.0, -29.0, -456.0)
                snapshots.values.asSequence()
                    .map { it.data.player }
                    .filter { it.isOnline && it.uniqueId != seeker.uniqueId }
                    .forEach { participant ->
                        participant.playSound(activationLocation, Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                            org.bukkit.SoundCategory.MASTER, 1.2f, 0.65f)
                    }
                world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,
                    activationLocation, 65, 2.0, 1.2, 2.0, 0.04)
                task = object : BukkitRunnable(ownerClass.abilityScope, TickPolicy.SESSION) {
                    var tick = 0
                    override fun run() {
                        if (activeSession !== this@Session) {
                            cancel()
                            return
                        }
                        if (!seeker.player.isOnline || !hider.player.isOnline || seeker.entityStatus.isDead || hider.entityStatus.isDead) {
                            finish()
                            cancel()
                            return
                        }
                        if (tick == 1) CooldownManager.pauseCooldown(seeker.player, ownerClass.hideAndSeekSkill)
                        if (!hidingComplete) {
                            seeker.player.stopAllSounds()
                            if (tick % 20 == 0) {
                                seeker.player.sendActionBar(miniMessage.deserialize("<yellow><bold>상대가 숨는 중입니다..."))
                                hider.player.sendActionBar(miniMessage.deserialize("<red><bold>문을 닫아 숨기를 완료하세요."))
                            }
                        } else if (!found && !duel) {
                            searchTicks++
                            if (tick % 20 == 0) {
                                val color = when (attempts) {
                                    1 -> "<dark_red>"
                                    2 -> "<red>"
                                    else -> "<gold>"
                                }
                                seeker.player.sendActionBar(miniMessage.deserialize("$color<bold>남은 문 열기: ${attempts}회"))
                            }
                            renderTension()
                        }
                        tick++
                    }
                }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L)
            }

            fun handleInteract(event: PlayerInteractEvent): Boolean {
                if (event.action != Action.RIGHT_CLICK_BLOCK) return false
                val player = event.player
                if (player.uniqueId != seeker.uniqueId && player.uniqueId != hider.uniqueId) return false
                val clicked = normalizeDoor(event.clickedBlock ?: return false) ?: return false
                val coordinate = Triple(clicked.x, clicked.y, clicked.z)
                val room = rooms.firstOrNull { coordinate in it.doors }
                val corridorDoor = coordinate in corridorDoors
                if (room == null && !corridorDoor) return false
                event.isCancelled = true
                val wasOpen = (clicked.blockData as? Openable)?.isOpen == true
                if (!soloTraining && player.uniqueId == hider.uniqueId) {
                    if (hidingComplete) return true
                    setDoorOpen(clicked, !wasOpen)
                    if (room == null) return true
                    if (!wasOpen) {
                        hiderOpenedDoors += coordinate
                    } else if (coordinate in hiderOpenedDoors) {
                        if (room.bounds.contains(hider.player.boundingBox.center)) {
                            completeHiding(coordinate)
                        } else {
                            hider.player.sendMiniMessage("<red><bold>[!] 방 안으로 완전히 들어간 뒤 문을 닫으세요.")
                        }
                    }
                    return true
                }
                if (!hidingComplete || found || duel) return true
                setDoorOpen(clicked, !wasOpen)
                if (room == null) return true
                if (!wasOpen && coordinate !in seekerOpenedDoors) {
                    seekerOpenedDoors += coordinate
                    attempts = (attempts - 1).coerceAtLeast(0)
                    if (attempts == 0) openAllDoors()
                    if (coordinate == hiddenDoor) {
                        found = true
                        if (soloTraining) {
                            seeker.player.sendMiniMessage("<green><bold>[모의 숨바꼭질]</bold> <white>가상의 플레이어가 숨은 방을 찾았습니다!")
                            object : BukkitRunnable(ownerClass.abilityScope, TickPolicy.SESSION) {
                                override fun run() {
                                    if (activeSession === this@Session) finish()
                                }
                            }.runTaskLater(ClassWarPlugin.instance, 20L)
                            return true
                        }
                        controls.getValue(hider.uniqueId).allow(Control.MOVE, true)
                        controls.getValue(hider.uniqueId).allow(Control.ATTACK, true)
                        hider.player.sendMiniMessage("<red><bold>[!] 술래에게 발각되었습니다!")
                        seeker.player.sendMiniMessage("<gold><bold>[!] 숨은 플레이어를 찾았습니다. 무기로 공격하세요!")
                    } else {
                        extinguishRandomEmptyRoom()
                        if (attempts <= 0) {
                            if (soloTraining) {
                                seeker.player.sendMiniMessage("<red><bold>[모의 숨바꼭질]</bold> <gray>가상의 플레이어를 찾지 못했습니다.")
                                object : BukkitRunnable(ownerClass.abilityScope, TickPolicy.SESSION) {
                                    override fun run() {
                                        if (activeSession === this@Session) finish()
                                    }
                                }.runTaskLater(ClassWarPlugin.instance, 20L)
                            } else {
                                beginDuel()
                            }
                        }
                    }
                }
                return true
            }

            fun canExecute(attacker: Player, victim: Player): Boolean {
                if (soloTraining) return false
                val pair = setOf(attacker.uniqueId, victim.uniqueId)
                val hasWeapon = attacker.inventory.itemInMainHand.type.name.endsWith("_SWORD")
                return hasWeapon && pair == setOf(seeker.uniqueId, hider.uniqueId) && (found || duel)
            }

            fun blocksDamage(attacker: Player, victim: Player): Boolean {
                val involved = attacker.uniqueId in snapshots || victim.uniqueId in snapshots
                return involved && !canExecute(attacker, victim)
            }

            fun finish() {
                if (activeSession !== this) return
                activeSession = null
                task?.cancel()
                task = null
                blockSnapshots.values.toList().asReversed().forEach { it.update(true, false) }
                controls.values.forEach { it.close() }
                snapshots.values.forEach { snapshot ->
                    val data = snapshot.data
                    val player = data.player
                    if (player.gameMode == GameMode.SPECTATOR) player.spectatorTarget = null
                    player.gameMode = snapshot.gameMode
                    player.inventory.storageContents = cloneItems(snapshot.storage)
                    player.inventory.armorContents = cloneItems(snapshot.armor)
                    player.inventory.extraContents = cloneItems(snapshot.extra)
                    player.removePotionEffect(PotionEffectType.BLINDNESS)
                    snapshot.blindness?.let(player::addPotionEffect)
                    player.isGlowing = snapshot.glowing
                    player.fireTicks = snapshot.fireTicks
                    if (data == seeker || data == hider) data.returnFromAbility(snapshot.location)
                }
                deadSpectatorSnapshots.values.forEach { snapshot ->
                    val spectator = snapshot.player
                    if (!spectator.isOnline) return@forEach
                    spectator.spectatorTarget = null
                    spectator.gameMode = snapshot.gameMode
                    spectator.teleport(snapshot.location)
                    if (snapshot.gameMode == GameMode.SPECTATOR && snapshot.spectatorTarget?.isValid == true) {
                        spectator.spectatorTarget = snapshot.spectatorTarget
                    }
                }
                borderExpansion?.restore()
                borderExpansion = null
                effectSuppression?.close()
                effectSuppression = null
                game.isPaused = false
                resumeCooldowns()
                val hiddenDisplays = displaySuppression
                displaySuppression = null
                hiddenDisplays?.close()
                val center = seeker.player.location
                center.world.spawnParticle(Particle.POOF, center, 38, 1.0, 0.8, 1.0, 0.1)
                center.world.playSound(center, Sound.BLOCK_TRIAL_SPAWNER_CLOSE_SHUTTER, 0.85f, 1.15f)
            }

            private fun completeHiding(doorCoordinate: Triple<Int, Int, Int>) {
                if (hidingComplete) return
                hidingComplete = true
                hiddenDoor = doorCoordinate
                searchTicks = 0
                controls.getValue(hider.uniqueId).allow(Control.MOVE, false)
                closeAllDoors()
                barrierBlocks.forEach { (x, y, z) -> setBlock(world.getBlockAt(x, y, z), Material.AIR) }
                controls.getValue(seeker.uniqueId).allow(Control.MOVE, true)
                controls.getValue(seeker.uniqueId).allow(Control.ATTACK, true)
                seeker.player.showTitle(Title.title(
                    miniMessage.deserialize("<red><bold>찾기 시작!"),
                    miniMessage.deserialize("<gray>문을 열 수 있는 횟수는 4회입니다."),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400)),
                ))
                hider.player.sendMiniMessage("<green><bold>[!] 숨기 완료. 움직이지 말고 기다리세요.")
                world.playSound(seeker.player.location, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f)
            }

            private fun beginDuel() {
                duel = true
                controls.getValue(hider.uniqueId).allow(Control.MOVE, true)
                controls.getValue(hider.uniqueId).allow(Control.ATTACK, true)
                controls.getValue(seeker.uniqueId).allow(Control.MOVE, true)
                controls.getValue(seeker.uniqueId).allow(Control.ATTACK, true)
                giveWeapon(hider.player, "<dark_purple><bold>미지의 힘")
                seeker.player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true))
                listOf(seeker, hider).distinct().forEach {
                    it.player.sendMiniMessage("<dark_red><bold>[!] 탐색 실패. 이제 먼저 상대를 공격하는 플레이어가 승리합니다.")
                }
                world.spawnParticle(Particle.SCULK_SOUL, seeker.player.location.add(0.0, 1.0, 0.0), 45, 1.0, 0.8, 1.0, 0.1)
                world.playSound(seeker.player.location, Sound.ENTITY_WARDEN_ROAR, 0.85f, 0.7f)
            }

            private fun extinguishRoom(room: Room) {
                room.lights.forEach { (x, y, z) ->
                    val light = world.getBlockAt(x, y, z)
                    val location = light.location.add(0.5, 0.5, 0.5)
                    setBlock(light, Material.AIR)
                    world.spawnParticle(Particle.LARGE_SMOKE, location, 16, 0.42, 0.35, 0.42, 0.055)
                    world.spawnParticle(Particle.SOUL, location, 7, 0.25, 0.25, 0.25, 0.035)
                    world.playSound(location, Sound.BLOCK_COPPER_BULB_TURN_OFF, 0.85f, 0.55f)
                }
                val center = room.bounds.center.toLocation(world)
                world.spawnParticle(Particle.LARGE_SMOKE, center, 42, 4.2, 1.6, 2.2, 0.045)
                world.spawnParticle(Particle.ASH, center, 34, 4.0, 1.4, 2.0, 0.018)
                world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.72f, 0.5f)
            }

            private fun extinguishRandomEmptyRoom() {
                val occupiedRoomIds = rooms.asSequence()
                    .filter { room ->
                        hiddenDoor in room.doors ||
                            (!soloTraining && hider.player.isOnline &&
                                room.bounds.contains(hider.player.boundingBox.center))
                    }
                    .mapTo(hashSetOf(), Room::id)
                val room = rooms.filter { it.id !in occupiedRoomIds && it.id !in extinguishedRoomIds }
                    .randomOrNull()
                    ?: return
                extinguishedRoomIds += room.id
                extinguishRoom(room)
            }

            private fun renderTension() {
                val heartbeatInterval = when (attempts) {
                    1 -> 11
                    2 -> 16
                    3 -> 23
                    else -> 31
                }
                if (searchTicks % heartbeatInterval == 0) {
                    val pitch = when (attempts) {
                        1 -> 1.35f
                        2 -> 1.15f
                        3 -> 0.95f
                        else -> 0.78f
                    }
                    listOf(seeker.player, hider.player).distinct().filter { it.isOnline }.forEach { participant ->
                        participant.playSound(participant.location, Sound.ENTITY_WARDEN_HEARTBEAT, 0.58f, pitch)
                    }
                }
                if (searchTicks % 8 == 0) {
                    world.spawnParticle(Particle.ASH, seeker.player.location.add(0.0, 1.0, 0.0), 2, 0.65, 0.45, 0.65, 0.008)
                }
                if (searchTicks % 140 == 0) {
                    world.playSound(seeker.player.location, Sound.AMBIENT_CAVE, 0.72f, 0.55f)
                }
            }

            private fun prepareMap() {
                barrierBlocks.forEach { (x, y, z) -> setBlock(world.getBlockAt(x, y, z), Material.BARRIER) }
                closeAllDoors()
            }

            private fun closeAllDoors() {
                (rooms.flatMap { it.doors } + corridorDoors).forEach { (x, y, z) ->
                    val lower = world.getBlockAt(x, y, z)
                    snapshotBlock(lower)
                    snapshotBlock(lower.getRelative(0, 1, 0))
                    for (block in listOf(lower, lower.getRelative(0, 1, 0))) {
                        val data = block.blockData
                        if (data is Openable) {
                            data.isOpen = false
                            block.setBlockData(data, false)
                        }
                    }
                }
            }

            private fun openAllDoors() {
                (rooms.flatMap { it.doors } + corridorDoors).forEach { (x, y, z) ->
                    val lower = world.getBlockAt(x, y, z)
                    snapshotBlock(lower)
                    snapshotBlock(lower.getRelative(0, 1, 0))
                    for (block in listOf(lower, lower.getRelative(0, 1, 0))) {
                        val data = block.blockData as? Openable ?: continue
                        data.isOpen = true
                        block.setBlockData(data, false)
                    }
                }
                listOf(seeker.player, hider.player).distinct().filter { it.isOnline }.forEach { participant ->
                    participant.sendMiniMessage("<dark_red><bold>[!] 문 열기 기회가 모두 소모되어 모든 문이 열렸습니다.")
                }
                world.playSound(seeker.player.location, Sound.BLOCK_IRON_DOOR_OPEN, 1.2f, 0.65f)
            }

            private fun setDoorOpen(lowerDoor: Block, open: Boolean) {
                listOf(lowerDoor, lowerDoor.getRelative(0, 1, 0)).forEach { block ->
                    val data = block.blockData as? Openable ?: return@forEach
                    data.isOpen = open
                    block.setBlockData(data, false)
                }
                world.playSound(
                    lowerDoor.location,
                    if (open) Sound.BLOCK_IRON_DOOR_OPEN else Sound.BLOCK_IRON_DOOR_CLOSE,
                    0.65f,
                    1.15f,
                )
            }

            private fun setBlock(block: Block, material: Material) {
                snapshotBlock(block)
                block.setType(material, false)
            }

            private fun snapshotBlock(block: Block) {
                val key = "${block.x}:${block.y}:${block.z}"
                blockSnapshots.putIfAbsent(key, block.state)
            }

            private fun pauseCooldowns() {
                snapshots.values.forEach { snapshot ->
                    snapshot.data.gameClasses.flatMap { it.skills }.forEach { skill ->
                        CooldownManager.pauseCooldown(snapshot.data.player, skill)
                    }
                }
            }

            private fun resumeCooldowns() {
                snapshots.values.forEach { snapshot ->
                    snapshot.data.gameClasses.flatMap { it.skills }.forEach { skill ->
                        CooldownManager.resumeCooldown(snapshot.data.player, skill)
                    }
                }
            }
        }

        fun handleInteract(event: PlayerInteractEvent): Boolean = activeSession?.handleInteract(event) ?: false

        fun handleDamage(event: EntityDamageByEntityEvent): Boolean {
            val session = activeSession ?: return false
            val attacker = event.damager as? Player ?: return false
            val victim = event.entity as? Player ?: return false
            if (session.canExecute(attacker, victim)) {
                session.finish()
                event.damage = victim.health + 2048.0
                victim.world.spawnParticle(Particle.SWEEP_ATTACK, victim.boundingBox.center.toLocation(victim.world), 18, 0.6, 0.8, 0.6, 0.08)
                victim.world.playSound(victim.location, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.65f)
                return false
            }
            if (session.blocksDamage(attacker, victim)) {
                event.isCancelled = true
                return true
            }
            return false
        }

        fun handlePlayerDeath(playerId: UUID) {
            val session = activeSession ?: return
            if (playerId in session.snapshots) session.finish()
        }

        private fun startSession(owner: HideAndSeek, target: PlayerData, moveDeadSpectatorsOnce: Boolean) {
            if (activeSession != null || target.entityStatus.isDead || !target.player.isOnline) return
            Session(owner, target, moveDeadSpectatorsOnce).start()
        }

        private fun endForGame(game: Game) {
            activeSession?.takeIf { it.game === game }?.finish()
        }

        private fun endForPlayer(playerId: UUID) {
            activeSession?.takeIf { playerId in it.snapshots }?.finish()
        }

        private fun normalizeDoor(block: Block): Block? {
            val data = block.blockData as? Door ?: return null
            return if (data.half == Bisected.Half.TOP) block.getRelative(0, -1, 0) else block
        }

        private fun snapshot(data: PlayerData): PlayerSnapshot {
            val player = data.player
            return PlayerSnapshot(
                data, player.location.clone(), player.gameMode,
                cloneItems(player.inventory.storageContents), cloneItems(player.inventory.armorContents),
                cloneItems(player.inventory.extraContents),
                player.getPotionEffect(PotionEffectType.BLINDNESS),
                player.isGlowing, player.fireTicks,
            )
        }

        private fun cloneItems(items: Array<ItemStack?>): Array<ItemStack?> =
            items.map { it?.clone() }.toTypedArray()

        private fun giveWeapon(player: Player, name: String) {
            player.inventory.setItem(0, ItemStack(Material.IRON_SWORD).apply {
                itemMeta = itemMeta.apply { displayName(miniMessage.deserialize(name)) }
            })
        }
    }
}
