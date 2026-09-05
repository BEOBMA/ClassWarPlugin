package org.beobma.classWarPlugin.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.GameManager
import org.beobma.classWarPlugin.manager.GameManager.abilityClassSuggestions
import org.beobma.classWarPlugin.manager.GameManager.assignedAbilityNames
import org.beobma.classWarPlugin.manager.GameManager.forceAssignAbility
import org.beobma.classWarPlugin.manager.GameManager.forceRemoveAbility
import org.beobma.classWarPlugin.manager.GameManager.stop
import org.beobma.classWarPlugin.manager.GameManager.stopTraining
import org.beobma.classWarPlugin.manager.GameManager.tailTargetPlayer
import org.beobma.classWarPlugin.manager.InventoryManager.openClassListInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openConfigInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openGameModeInventory
import org.beobma.classWarPlugin.manager.InventoryManager.openTrainingClassListInventory
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager
import org.beobma.classWarPlugin.updater.GitHubReleaseUpdater.UpdateResult
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.Locale

class Command : Listener, CommandExecutor, TabCompleter {
    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        if (!cmd.name.equals("classwar", ignoreCase = true)) return false
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            sendHelp(sender, args.getOrNull(1)?.toIntOrNull() ?: 1)
            return true
        }

        when (args[0].lowercase(Locale.ROOT)) {
            "update" -> return handleUpdate(sender)
            "reload" -> return handleReload(sender)
            "assign", "give", "능력배정" -> return handleAssign(sender, args)
            "remove", "take", "능력제거" -> return handleRemove(sender, args)
            "abilities", "ability", "능력" -> return handleAbilities(sender, args)
            "keyword", "keywords", "키워드" -> return handleKeyword(sender, args)
            "stop" -> {
                if (!requireOperator(sender)) return true
                if (!isGaming()) {
                    sender.sendWarningMessage("진행중인 게임이 없습니다.")
                    return true
                }
                game?.stop()
                sender.sendInfoMessage("게임을 종료했습니다.")
                return true
            }
        }

        val player = sender as? Player ?: run {
            sender.sendWarningMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }
        when (args[0].lowercase(Locale.ROOT)) {
            "config" -> player.openConfigInventory()
            "start" -> {
                if (!requireOperator(sender)) return true
                if (isGaming()) sender.sendWarningMessage("이미 진행중인 게임이 있습니다.")
                else player.openGameModeInventory()
            }
            "classlist", "list" -> {
                if (isGaming()) sender.sendWarningMessage("게임 진행 중 사용할 수 없는 명령어입니다.")
                else player.openClassListInventory(0)
            }
            "training", "tranning", "practice" -> {
                when {
                    isGaming() -> sender.sendWarningMessage("게임 진행 중 사용할 수 없는 명령어입니다.")
                    PlayerTagManager.isTraining(player) -> sender.sendWarningMessage("이미 연습 중입니다.")
                    else -> player.openTrainingClassListInventory(0)
                }
            }
            "exit" -> {
                when {
                    isGaming() -> sender.sendWarningMessage("게임 진행 중 사용할 수 없는 명령어입니다.")
                    !PlayerTagManager.isTraining(player) -> sender.sendWarningMessage("연습 중에만 사용할 수 있습니다.")
                    else -> player.stopTraining()
                }
            }
            "target", "표적" -> {
                val target = tailTargetPlayer(player)
                if (target == null) {
                    sender.sendWarningMessage("현재 배정된 꼬리잡기 표적이 없습니다.")
                } else {
                    player.compassTarget = target.location
                    sender.sendInfoMessage("현재 표적은 <gold><bold>${target.name}</bold></gold>님입니다. 나침반 위치를 갱신했습니다.")
                }
            }
            else -> {
                sender.sendWarningMessage("'${args[0]}'은 알 수 없는 명령어입니다.")
                sendHelp(sender, 1)
            }
        }
        return true
    }

    private fun handleAssign(sender: CommandSender, args: Array<String>): Boolean {
        if (!requireOperator(sender)) return true
        if (args.size < 3) {
            sender.sendWarningMessage("사용법: /cw assign <플레이어> <능력> [슬롯]")
            return true
        }
        val target = Bukkit.getPlayerExact(args[1]) ?: run {
            sender.sendWarningMessage("접속 중인 플레이어 '${args[1]}'을(를) 찾을 수 없습니다.")
            return true
        }
        val slot = args.getOrNull(3)?.toIntOrNull() ?: 1
        val result = forceAssignAbility(target, args[2], slot)
        sender.sendAbilityResult(result)
        if (result.success && sender != target) target.sendInfoMessage("관리자가 능력 배정을 변경했습니다.")
        return true
    }

    private fun handleRemove(sender: CommandSender, args: Array<String>): Boolean {
        if (!requireOperator(sender)) return true
        if (args.size < 3) {
            sender.sendWarningMessage("사용법: /cw remove <플레이어> <능력|슬롯|all>")
            return true
        }
        val target = Bukkit.getPlayerExact(args[1]) ?: run {
            sender.sendWarningMessage("접속 중인 플레이어 '${args[1]}'을(를) 찾을 수 없습니다.")
            return true
        }
        val result = forceRemoveAbility(target, args[2])
        sender.sendAbilityResult(result)
        if (result.success && sender != target) target.sendInfoMessage("관리자가 배정된 능력을 제거했습니다.")
        return true
    }

    private fun handleAbilities(sender: CommandSender, args: Array<String>): Boolean {
        val target = if (args.size >= 2) {
            if (!sender.isOp && (sender !is Player || !sender.name.equals(args[1], ignoreCase = true))) {
                sender.sendWarningMessage("다른 플레이어의 능력은 관리자만 확인할 수 있습니다.")
                return true
            }
            Bukkit.getPlayerExact(args[1])
        } else {
            sender as? Player
        } ?: run {
            sender.sendWarningMessage("확인할 접속 중인 플레이어를 지정해 주세요.")
            return true
        }
        val names = assignedAbilityNames(target) ?: run {
            sender.sendWarningMessage("${target.name}님은 게임 또는 연습에 참가하고 있지 않습니다.")
            return true
        }
        sender.sendInfoMessage(
            "${target.name}님의 능력: " + names.joinToString(" <dark_gray>+</dark_gray> ").ifEmpty { "<red>없음" },
        )
        return true
    }

    private fun handleKeyword(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 2) {
            val names = Keyword.describedEntries.joinToString("<gray>, </gray>") { it.string }
            sender.sendMessage(miniMessage.deserialize("<gold><bold>키워드 목록</bold></gold> <gray>- /cw keyword <키워드>로 효과를 확인하세요."))
            sender.sendMessage(miniMessage.deserialize(names))
            return true
        }

        val keyword = Keyword.find(args.drop(1).joinToString(" ")) ?: run {
            sender.sendWarningMessage("해당 키워드를 찾을 수 없습니다. /cw keyword로 목록을 확인해 주세요.")
            return true
        }
        sender.sendMessage(miniMessage.deserialize(UtilManager.applyKeywords(keyword.requireDescription())))
        return true
    }

    private fun handleReload(sender: CommandSender): Boolean {
        if (!requireOperator(sender)) return true
        val plugin = ClassWarPlugin.instance
        plugin.reloadConfig()
        GameSettings.load(plugin.config)
        ClassBalanceManager.load(plugin.config, GameManager.gameClassList)
        sender.sendInfoMessage("config.yml과 클래스 밸런스 설정을 다시 불러왔습니다. 게임 공통 설정은 다음 게임부터 적용됩니다.")
        return true
    }

    private fun handleUpdate(sender: CommandSender): Boolean {
        if (!requireOperator(sender)) return true
        sender.sendInfoMessage("GitHub에서 최신 릴리스를 확인하고 있습니다...")
        ClassWarPlugin.instance.releaseUpdater.checkNow { result ->
            when (result) {
                is UpdateResult.UpToDate -> sender.sendInfoMessage("현재 버전(${result.currentVersion})이 최신 버전입니다.")
                is UpdateResult.CurrentIsNewer -> sender.sendInfoMessage(
                    "현재 버전(${result.currentVersion})이 최신 공개 릴리스(${result.latestVersion})보다 높습니다.",
                )
                is UpdateResult.PendingRestart -> sender.sendInfoMessage(
                    "${result.latestVersion} 업데이트가 이미 준비되어 있습니다. 서버를 재시작하면 적용됩니다.",
                )
                is UpdateResult.Downloaded -> sender.sendInfoMessage(
                    "${result.latestVersion} 업데이트를 내려받았습니다. 서버를 재시작하면 적용됩니다.",
                )
                is UpdateResult.Failed -> sender.sendWarningMessage("업데이트 확인 실패: ${result.reason}")
                UpdateResult.InProgress -> sender.sendWarningMessage("이미 업데이트를 확인하고 있습니다.")
            }
        }
        return true
    }

    private fun sendHelp(sender: CommandSender, requestedPage: Int) {
        val page = requestedPage.coerceIn(1, 2)
        sender.sendMessage(miniMessage.deserialize("<gold><bold>ClassWar 명령어 도움말</bold> <gray>($page/2)"))
        val lines = if (page == 1) listOf(
            "<yellow>/cw help [페이지] <gray>- 도움말을 표시합니다.",
            "<yellow>/cw classlist <gray>- 전체 능력 목록을 엽니다.",
            "<yellow>/cw training <gray>- 능력 연습을 시작합니다.",
            "<yellow>/cw exit <gray>- 능력 연습을 종료합니다.",
            "<yellow>/cw abilities [플레이어] <gray>- 현재 배정 능력을 확인합니다.",
            "<yellow>/cw keyword [키워드] <gray>- 키워드 목록이나 효과를 확인합니다.",
            "<yellow>/cw target <gray>- 꼬리잡기 표적을 확인하고 나침반을 갱신합니다.",
            "<dark_gray>관리자 명령은 /cw help 2에서 확인할 수 있습니다.",
        ) else listOf(
            "<yellow>/cw start <gray>- 게임 모드 선택 창을 엽니다.",
            "<yellow>/cw stop <gray>- 현재 게임을 종료합니다.",
            "<yellow>/cw assign <플레이어> <능력> [슬롯] <gray>- 능력을 강제 배정합니다.",
            "<yellow>/cw remove <플레이어> <능력|슬롯|all> <gray>- 배정 능력을 제거합니다.",
            "<yellow>/cw config <gray>- 게임 설정 창을 엽니다.",
            "<yellow>/cw reload <gray>- 설정 파일을 다시 불러옵니다.",
            "<yellow>/cw update <gray>- GitHub 최신 배포를 즉시 확인합니다.",
        )
        lines.forEach { sender.sendMessage(miniMessage.deserialize(it)) }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): List<String> {
        if (!command.name.equals("classwar", ignoreCase = true)) return emptyList()
        val playerCommands = listOf("help", "classlist", "training", "exit", "abilities", "keyword", "target")
        val adminCommands = listOf("start", "stop", "config", "assign", "remove", "reload", "update")
        return when (args.size) {
            1 -> (playerCommands + if (sender.isOp) adminCommands else emptyList())
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase(Locale.ROOT)) {
                "help" -> listOf("1", "2")
                "assign", "give", "remove", "take" -> Bukkit.getOnlinePlayers().map(Player::getName)
                "abilities", "ability" -> if (sender.isOp) Bukkit.getOnlinePlayers().map(Player::getName) else emptyList()
                "keyword", "keywords", "키워드" -> Keyword.describedEntries.flatMap { listOf(it.displayName, it.name) }
                else -> emptyList()
            }.filter { it.startsWith(args[1], ignoreCase = true) }
            3 -> when (args[0].lowercase(Locale.ROOT)) {
                "assign", "give" -> abilityClassSuggestions()
                "remove", "take" -> {
                    val target = Bukkit.getPlayerExact(args[1])
                    listOf("all", "1", "2") + (target?.let(::assignedAbilityNames) ?: emptyList())
                }
                else -> emptyList()
            }.filter { it.startsWith(args[2], ignoreCase = true) }
            4 -> if (args[0].equals("assign", ignoreCase = true) || args[0].equals("give", ignoreCase = true)) {
                listOf("1", "2").filter { it.startsWith(args[3]) }
            } else emptyList()
            else -> emptyList()
        }
    }

    private fun requireOperator(sender: CommandSender): Boolean {
        if (sender.isOp) return true
        sender.sendWarningMessage("이 명령어는 관리자만 사용할 수 있습니다.")
        return false
    }

    private fun CommandSender.sendAbilityResult(result: GameManager.AbilityChangeResult) {
        if (result.success) sendInfoMessage(result.message) else sendWarningMessage(result.message)
    }

    private fun CommandSender.sendWarningMessage(message: String) {
        sendMessage(miniMessage.deserialize("<red><bold>[!] $message"))
    }

    private fun CommandSender.sendInfoMessage(message: String) {
        sendMessage(miniMessage.deserialize("<green><bold>[ClassWar]</bold> <white>$message"))
    }
}
