package org.beobma.classWarPlugin.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.stop
import org.beobma.classWarPlugin.manager.GameManager.stopTraining
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openTrainingClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openConfigInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openGameModeInventory
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.updater.GitHubReleaseUpdater.UpdateResult
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.*

class Command : Listener, CommandExecutor, TabCompleter {
    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        if (cmd.name.equals("classwar", ignoreCase = true) && args.isNotEmpty()) {
            if (args[0].equals("update", ignoreCase = true)) {
                if (!sender.isOp) {
                    sender.sendWaringMessage("이 명령어는 관리자만 사용할 수 있습니다.")
                    return false
                }
                sender.sendInfoMessage("GitHub에서 최신 릴리스를 확인하고 있습니다...")
                ClassWarPlugin.instance.releaseUpdater.checkNow { result ->
                    when (result) {
                        is UpdateResult.UpToDate -> sender.sendInfoMessage(
                            "현재 버전(${result.currentVersion})이 최신 버전입니다.",
                        )

                        is UpdateResult.CurrentIsNewer -> sender.sendInfoMessage(
                            "현재 버전(${result.currentVersion})이 최신 공개 릴리스(${result.latestVersion})보다 높습니다.",
                        )

                        is UpdateResult.PendingRestart -> sender.sendInfoMessage(
                            "${result.latestVersion} 업데이트가 이미 준비되어 있습니다. 서버를 재시작하면 적용됩니다.",
                        )

                        is UpdateResult.Downloaded -> sender.sendInfoMessage(
                            "${result.latestVersion} 업데이트를 내려받았습니다. 서버를 재시작하면 적용됩니다.",
                        )

                        is UpdateResult.Failed -> sender.sendWaringMessage("업데이트 확인 실패: ${result.reason}")
                        UpdateResult.InProgress -> sender.sendWaringMessage("이미 업데이트를 확인하고 있습니다.")
                    }
                }
                return true
            }

            if (sender !is Player) {
                sender.sendWaringMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
                return false
            }

            when (args[0].lowercase(Locale.getDefault())) {
                "config" -> {
                    sender.openConfigInventory()
                    return true
                }

                "start" -> {
                    if (isGaming()) {
                        sender.sendWaringMessage("이미 진행중인 게임이 있습니다.")
                        return false
                    }

                    if (!sender.isOp) {
                        sender.sendWaringMessage("이 명령어는 관리자만 사용할 수 있습니다.")
                        return false
                    }
                    sender.openGameModeInventory()
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

                "training", "tranning" -> {
                    if (isGaming()) {
                        sender.sendWaringMessage("게임 진행 중 사용할 수 없는 명령어입니다.")
                        return false
                    }

                    if (PlayerTagManager.hasTag(sender, "isTraining")) {
                        sender.sendWaringMessage("이미 훈련 중입니다.")
                        return false
                    }

                    sender.openTrainingClassListInventory(0)
                }

                "exit" -> {
                    if (isGaming()) {
                        sender.sendWaringMessage("게임 진행 중 사용할 수 없는 명령어입니다.")
                        return false
                    }

                    if (!PlayerTagManager.hasTag(sender, "isTraining")) {
                        sender.sendWaringMessage("훈련 중에만 사용할 수 있습니다.")
                        return false
                    }

                    sender.stopTraining()
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
                1 -> listOf("start", "stop", "config", "classlist", "training", "exit", "update")
                    .filter { it.startsWith(args[0], ignoreCase = true) }

                else -> emptyList()
            }
        }
        return emptyList()
    }


    private fun CommandSender.sendWaringMessage(msg: String) {
        sendMessage(miniMessage.deserialize("<red><bold>[!] $msg"))
    }

    private fun CommandSender.sendInfoMessage(msg: String) {
        sendMessage(miniMessage.deserialize("<green><bold>[ClassWar]</bold> <white>$msg"))
    }
}
