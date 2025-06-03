package org.beobma.classWarPlugin.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.start
import org.beobma.classWarPlugin.manager.GameManager.startTest
import org.beobma.classWarPlugin.manager.GameManager.stop
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.*

class Command : Listener, CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        if (cmd.name.equals("classwar", ignoreCase = true) && args.isNotEmpty()) {
            if (sender !is Player) {
                sender.sendWaringMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
                return false
            }

            when (args[0].lowercase(Locale.getDefault())) {
                "start" -> {
                    if (isGaming()) {
                        sender.sendWaringMessage("이미 진행중인 게임이 있습니다.")
                        return false
                    }

                    if (!sender.isOp) {
                        sender.sendWaringMessage("이 명령어는 관리자만 사용할 수 있습니다.")
                        return false
                    }
                    val game = Game(mutableListOf())
                    val players = Bukkit.getOnlinePlayers().map { PlayerData(it, game) }.toHashSet()
                    game.playerDatas.addAll(players.filter { !it.player.scoreboardTags.contains("isTest") })

//                    if (game.players.size <= 1) {
//                        sender.sendWaringMessage("참가자가 2명 이상이여야 게임을 시작할 수 있습니다.")
//                        return false
//                    }
                    game.start()
                    return true
                }

                "stop" -> {
                    if (!isGaming()) {
                        sender.sendWaringMessage("진행중인 게임이 없습니다.")
                        return false
                    }

                    if (!sender.isOp) {
                        sender.sendWaringMessage("이 명령어는 관리자만 사용할 수 있습니다.")
                        return false
                    }

                    game?.stop()
                }

                "classlist" -> {
                    if (isGaming()) {
                        sender.sendWaringMessage("게임 진행 중 사용할 수 없는 명령어입니다.")
                        return false
                    }

                    sender.openClassListInventory(0)
                }

                "test" -> {
                    if (isGaming()) {
                        sender.sendWaringMessage("게임 진행 중 사용할 수 없는 명령어입니다.")
                        return false
                    }

                    if (sender.scoreboardTags.contains("isTest")) {
                        sender.sendWaringMessage("이미 테스트 중입니다.")
                        return false
                    }

                    sender.startTest()
                }

                else -> {
                    sender.sendWaringMessage("'${args[0]}'은 알 수 없는 명령어입니다.")
                    return false
                }
            }
            return false
        }
        return false
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<String>
    ): List<String> {
        if (command.name.equals("classwar", ignoreCase = true)) {
            return when (args.size) {
                1 -> listOf("start", "stop", "classlist")

                else -> emptyList()
            }
        }
        return emptyList()
    }


    private fun CommandSender.sendWaringMessage(msg: String) {
        sendMessage(MiniMessage.miniMessage().deserialize("<red><bold>[!] $msg"))
    }
}