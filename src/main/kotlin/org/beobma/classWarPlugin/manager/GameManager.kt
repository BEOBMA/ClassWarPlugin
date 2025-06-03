package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GameSetDetailType
import org.beobma.classWarPlugin.game.GameSetType
import org.beobma.classWarPlugin.game.GameSetType.*
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.list.Sniper
import org.beobma.classWarPlugin.gameClass.list.Assassin
import org.beobma.classWarPlugin.gameClass.list.Astronomer
import org.beobma.classWarPlugin.gameClass.list.Bard
import org.beobma.classWarPlugin.gameClass.list.Berserker
import org.beobma.classWarPlugin.gameClass.list.DarkWizard
import org.beobma.classWarPlugin.gameClass.list.Duelist
import org.beobma.classWarPlugin.gameClass.list.FireWizard
import org.beobma.classWarPlugin.gameClass.list.Gambler
import org.beobma.classWarPlugin.gameClass.list.IceWizard
import org.beobma.classWarPlugin.gameClass.list.Judge
import org.beobma.classWarPlugin.gameClass.list.Knight
import org.beobma.classWarPlugin.gameClass.list.LandWizard
import org.beobma.classWarPlugin.gameClass.list.LightWizard
import org.beobma.classWarPlugin.gameClass.list.LightningWizard
import org.beobma.classWarPlugin.gameClass.list.Mathematician
import org.beobma.classWarPlugin.gameClass.list.Paladin
import org.beobma.classWarPlugin.gameClass.list.Physicist
import org.beobma.classWarPlugin.gameClass.list.Priests
import org.beobma.classWarPlugin.gameClass.list.RuneWizard
import org.beobma.classWarPlugin.gameClass.list.SpaceOperator
import org.beobma.classWarPlugin.gameClass.list.TimeManiqulator
import org.beobma.classWarPlugin.gameClass.list.Warlock
import org.beobma.classWarPlugin.gameClass.list.WaterWizard
import org.beobma.classWarPlugin.gameClass.list.WindWizard
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.manager.InventoryManager.openClassPickInventory
import org.beobma.classWarPlugin.manager.PlayerManager.classSet
import org.beobma.classWarPlugin.manager.UtilManager.getPlayerMaxHealth
import org.beobma.classWarPlugin.manager.UtilManager.isInArea
import org.beobma.classWarPlugin.map.Map
import org.beobma.classWarPlugin.map.list.Forest
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.player.TeamType.*
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

object GameManager{
    private val miniMessage = MiniMessage.miniMessage()
    private val gameMapList: List<Map> = listOf(
        Forest()
    )

    val gameClassList: List<GameClass> = listOf(
        Berserker(), Sniper(), FireWizard(), WaterWizard(), TimeManiqulator(), LandWizard(), WindWizard(),
        Gambler(), Knight(), SpaceOperator(), LightningWizard(), LightWizard(), DarkWizard(),
        Priests(), Warlock(), Mathematician(), Physicist(), Paladin(), Bard(), Judge(),
        Duelist(), Astronomer(), Assassin(), IceWizard(), RuneWizard()
    )

    fun Game.start() {
        game = this
        playerDatas.forEach { playerData ->
            val player = playerData.player
            val playerStatus = playerData.playerStatus

            playerStatus.canAttack = false
            playerStatus.isAttackable = false
            player.fireTicks = 0
            player.inventory.clear()
            player.gameMode = GameMode.ADVENTURE
            player.health = player.getPlayerMaxHealth()
        }
        sendNotification("잠시 후 게임을 시작합니다.")
        object : BukkitRunnable() {
            override fun run() {
                sendNotification("- 참가자 목록 -")
                playerDatas.forEachIndexed { index, playerData ->
                    sendNotification("[$index + 1]. ${playerData.player.name}")
                }
                object : BukkitRunnable() {
                    override fun run() {
                        mapPickStart()
                    }
                }.runTaskLater(ClassWarPlugin.instance, 30L)
            }
        }.runTaskLater(ClassWarPlugin.instance, 30L)
    }

    fun Game.stop() {
        ClassWarPlugin.instance.server.scheduler.cancelTasks(ClassWarPlugin.instance)

        this.playerDatas.forEach { playerData ->
            val player = playerData.player
            player.inventory.clear()
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }
            val playerTags = player.scoreboardTags.toList()
            for (tag in playerTags) {
                player.removeScoreboardTag(tag)
            }
            player.playerListName(MiniMessage.miniMessage().deserialize(player.name))
            player.fireTicks = 0
            player.inventory.clear()
            player.health = player.getPlayerMaxHealth()

            player.teleport(Location(Bukkit.getWorld("world"), 10.0, -60.0, 0.0, 90F, 0F))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "scoreboard players reset @a")
            player.gameMode = GameMode.ADVENTURE
        }
        game = null
    }

    private fun Game.mapPickStart() {
        val players = playerDatas.map { it.player }
        this.mapList.addAll(gameMapList)
        sendNotification("잠시 후 전장이 랜덤으로 선택됩니다.")

        var timer = 0
        object : BukkitRunnable() {
            override fun run() {
                if (timer == 80) {
                    cancel()
                    val map = mapList.random()
                    players.sendTitleNotification(miniMessage.deserialize(map.name))
                    sendNotification("선택된 전장은 ${map.name}입니다.")
                    this@mapPickStart.map = map
                    players.forEach { player ->
                        player.location.world.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F)
                    }
                    return
                }
                if (timer >= 60) {
                    if (timer % 2 == 0) {
                        val map = mapList.random()
                        players.sendTitleNotification(miniMessage.deserialize(map.name))
                        players.forEach { player ->
                            player.location.world.playSound(player.location, Sound.BLOCK_LEVER_CLICK, 1.0F, 2.0F)
                        }
                    }
                }

                timer++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 30L, 1L)

        object : BukkitRunnable() {
            override fun run() {
                teamPickStart()
            }
        }.runTaskLater(ClassWarPlugin.instance, 140L)
    }

    private fun Game.teamPickStart() {
        val players = playerDatas.map { it.player }

        sendNotification("잠시 후 게임을 진행할 팀을 결정합니다.")

        object : BukkitRunnable() {
            override fun run() {
                players.forEach { player ->
                    player.teleport(Location(Bukkit.getWorld("world"), 1.0, -60.0, -16.0, 0F, 0F))
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_GUITAR, 1.0F, 2.0F)
                }

                sendNotification("10초 후, 플레이어가 위치한 양털의 색에 따라 팀이 등록됩니다.")
                sendNotification("흰색의 경우 관전자입니다.")

                object : BukkitRunnable() {
                    override fun run() {
                        this@teamPickStart.playerDatas.forEach { playerData ->
                            val player = playerData.player
                            if (player.isInArea(Location(Bukkit.getWorld("world"), 7.0, -55.0, -7.0), Location(Bukkit.getWorld("world"), 2.0, -61.0, -12.0))) {
                                playerData.team = Red
                                player.playerListName(MiniMessage.miniMessage().deserialize("<red>${player.name}"))
                            }
                            else if (player.isInArea(Location(Bukkit.getWorld("world"), -5.0, -55.0, -7.0), Location(Bukkit.getWorld("world"), 0.0, -61.0, -12.0))) {
                                playerData.team = Blue
                                player.playerListName(MiniMessage.miniMessage().deserialize("<blue>${player.name}"))
                            }
                            else {
                                playerData.team = Spectator
                            }

                            val redTeamPlayers = this@teamPickStart.playerDatas.filter { it.team == Red }
                            val blueTeamPlayers = this@teamPickStart.playerDatas.filter { it.team == Blue }
                            val spectatorTeamPlayers = this@teamPickStart.playerDatas.filter { it.team == Spectator }

//                            if (redTeamPlayers.isEmpty() || blueTeamPlayers.isEmpty()) {
//                                sendNotification("상대 팀이 존재하지 않습니다. 게임을 강제로 종료합니다.")
//                                stop()
//                                return
//                            }

                            sendNotification("팀 등록이 완료되었습니다.")
                            sendNotification("<red>레드 팀:")
                            redTeamPlayers.forEach { playerData ->
                                sendNotification("<red> ${playerData.player.name}")
                            }
                            sendNotification("<blue>블루 팀:")
                            blueTeamPlayers.forEach { playerData ->
                                sendNotification("<blue> ${playerData.player.name}")
                            }
                            sendNotification("관전자 팀:")
                            spectatorTeamPlayers.forEach { playerData ->
                                sendNotification(playerData.player.name)
                            }

                            object : BukkitRunnable() {
                                override fun run() {
                                    classPick()
                                }
                            }.runTaskLater(ClassWarPlugin.instance, 140L)
                        }
                    }
                }.runTaskLater(ClassWarPlugin.instance, 200L)
            }
        }.runTaskLater(ClassWarPlugin.instance, 30L)
    }

    private fun Game.classPick() {
        classPickOrder = playerDatas.filter { it.team != Spectator }.shuffled().toMutableList()
        classList.addAll(gameClassList)

        sendNotification("잠시 후 순차적으로 클래스를 선택합니다.")
        sendNotification("선택 순서:")
        classPickOrder.forEach { playerData ->
            val player = playerData.player
            when (playerData.team) {
                Red -> {
                    sendNotification("<red>${player.name}")
                }
                Blue -> {
                    sendNotification("<blue>${player.name}")
                }

                Spectator -> {
                    sendNotification(player.name)
                }
                null -> {
                    sendNotification(player.name)
                }
            }
        }

        object : BukkitRunnable() {
            override fun run() {
                val pickPlayer = classPickOrder.firstOrNull() ?: run {
                    ready()
                    return
                }

                pickPlayer.classPick()
            }
        }.runTaskLater(ClassWarPlugin.instance, 30L)
    }

    fun PlayerData.classPick() {
        when (team) {
            Red -> {
                sendNotification("<red>${player.name}</red>님이 클래스 선택 중입니다.")
            }
            Blue -> {
                sendNotification("<blue>${player.name}</blue>님이 클래스 선택 중입니다.")
            }
            else -> {
                sendNotification("${player.name}님이 클래스 선택 중입니다.")
            }
        }
        openClassPickInventory(0)
    }

    fun Player.startTest() {
        TODO("Not yet implemented")
    }

    fun Game.ready() {
        sendNotification("모든 플레이어가 클래스 선택을 마쳤습니다.")
        sendNotification("잠시 후 게임을 시작합니다.")

        object : BukkitRunnable() {
            override fun run() {
                if (map == null) {
                    return
                }

                playerDatas.forEach { playerData ->
                    val playerStatus = playerData.playerStatus
                    when (playerData.team) {
                        Red -> {
                            playerData.player.teleport(map!!.redTeamStartLocation)
                        }
                        Blue -> {
                            playerData.player.teleport(map!!.blueTeamStartLocation)
                        }
                        Spectator -> {
                            playerData.player.teleport(map!!.spectatorTeamStartLocation)
                        }
                        null -> {
                            playerData.player.teleport(map!!.spectatorTeamStartLocation)
                        }
                    }

                    playerData.classSet()
                    playerData.player.showTitle(Title.title(Component.text("Fight!").decorate(TextDecoration.BOLD), Component.text("무승부까지 3분").decorate(TextDecoration.BOLD)))
                    playerStatus.canAttack = true
                    playerStatus.isAttackable = true
                    object : BukkitRunnable() {
                        override fun run() {
                            gameSet(Draw, GameSetDetailType.Draw)
                        }
                    }.runTaskLater(ClassWarPlugin.instance, 3600L)

                    object  : BukkitRunnable() {
                        override fun run() {
                            val playerDatas = playerDatas.filter { !it.playerStatus.isDead }
                            playerDatas.forEach { playerData ->
                                val gameClass = playerData.gameClass
                                val passives = gameClass?.passives

                                passives?.forEach { passive ->
                                    if (passive is GameStatusHandler) {
                                        passive.onGameTimePasses()
                                    }
                                }
                            }
                        }
                    }.runTaskTimer(ClassWarPlugin.instance, 0L, 20L)
                }
            }
        }.runTaskLater(ClassWarPlugin.instance, 30L)
    }

    fun Game.gameSet(gameSetType: GameSetType, gameSetDetailType: GameSetDetailType) {
        val players = playerDatas.map { it.player }
        when (gameSetType) {
            RedTeamWin -> {
                players.sendTitleNotification(MiniMessage.miniMessage().deserialize("<red><bold>레드팀 승리"), gameSetDetailType.component)
            }
            BlueTeamWin -> {
                players.sendTitleNotification(MiniMessage.miniMessage().deserialize("<blue><bold>블루팀 승리"), gameSetDetailType.component)
            }
            Draw -> {
                players.sendTitleNotification(MiniMessage.miniMessage().deserialize("<bold>무승부"), gameSetDetailType.component)
            }
        }
    }


    private fun sendNotification(msg: String) {
        val players = Bukkit.getOnlinePlayers()
        players.forEach { player ->
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_GUITAR, 1.0F, 2.0F)
        }
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize("[!] $msg"))
    }
    private fun List<Player>.sendTitleNotification(msg: Component, msg2: Component = Component.text("")) {
        forEach {
            it.showTitle(Title.title(msg, msg2))
        }
    }
}