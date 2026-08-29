package org.beobma.classWarPlugin

import org.beobma.classWarPlugin.command.Command
import org.beobma.classWarPlugin.info.Info
import org.beobma.classWarPlugin.manager.GameManager
import org.beobma.classWarPlugin.listener.OnEntityDamageByEntityEvent
import org.beobma.classWarPlugin.listener.OnEntityDamageEvent
import org.beobma.classWarPlugin.listener.OnEntityRegainHealthEvent
import org.beobma.classWarPlugin.listener.OnEntityDeathEvent
import org.beobma.classWarPlugin.listener.OnDamageIndicatorEvent
import org.beobma.classWarPlugin.listener.OnFoodChangeEvent
import org.beobma.classWarPlugin.listener.OnInventoryClickEvent
import org.beobma.classWarPlugin.listener.OnInventoryCloseEvent
import org.beobma.classWarPlugin.listener.OnPlayerDeathEvent
import org.beobma.classWarPlugin.listener.OnPlayerInteractEvent
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.listener.OnPlayerSkillUseEvent
import org.beobma.classWarPlugin.listener.OnPlayerMoveEvent
import org.beobma.classWarPlugin.listener.OnPlayerConnectionEvent
import org.beobma.classWarPlugin.listener.OnPlayerSwapHandItemsEvent
import org.beobma.classWarPlugin.listener.OnPlayerToggleSneakEvent
import org.beobma.classWarPlugin.listener.OnPlayerInputEvent
import org.beobma.classWarPlugin.listener.OnAsyncChatEvent
import org.beobma.classWarPlugin.listener.OnProjectileHitEvent
import org.beobma.classWarPlugin.listener.OnEntityShootBowEvent
import org.beobma.classWarPlugin.listener.OnBattleMapEvent
import org.beobma.classWarPlugin.game.GameSettings
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager
import org.beobma.classWarPlugin.manager.DamageIndicatorManager
import org.beobma.classWarPlugin.manager.StealthVisibilityManager
import org.beobma.classWarPlugin.manager.AttackableObjectManager
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.util.CourtroomMidiPlayer
import org.beobma.classWarPlugin.updater.GitHubReleaseUpdater
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.jar.JarFile

class ClassWarPlugin : JavaPlugin() {

    companion object {
        const val LUNAR_APOLLO_CHANNEL = "lunar:apollo"
        private const val PLUGIN_CLASS_PATH = "org/beobma/classWarPlugin/"
        private const val RELOCATED_LIBRARY_PATH = "${PLUGIN_CLASS_PATH}libs/"
        lateinit var instance: ClassWarPlugin
    }

    private var statusActionBarTask: BukkitTask? = null
    val releaseUpdater = GitHubReleaseUpdater(this)

    override fun onLoad() {
        preloadPluginClasses()
    }

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        GameSettings.load(config)
        ClassBalanceManager.load(config, GameManager.gameClassList)
        DamageIndicatorManager.start()
        AttackableObjectManager.start()
        CourtroomMidiPlayer.preload()

        registerClientDetectionChannel()
        registerEvents()
        startStatusActionBarTask()
        releaseUpdater.start()
        loggerInfo("플러그인이 정상적으로 활성화되었습니다.")
    }

    override fun onDisable() {
        statusActionBarTask?.cancel()
        releaseUpdater.stop()
        GameManager.run {
            Info.game?.stop()
            stopAllTraining()
        }
        StealthVisibilityManager.showAll()
        DamageIndicatorManager.shutdown()
        AttackableObjectManager.shutdown()
        server.messenger.unregisterIncomingPluginChannel(this)
        loggerInfo("플러그인이 정상적으로 비활성화되었습니다.")
    }

    private fun registerClientDetectionChannel() {
        server.messenger.registerIncomingPluginChannel(this, LUNAR_APOLLO_CHANNEL) { _, _, _ -> }
    }

    /**
     * Paper keeps reading classes lazily from the plugin JAR. If that JAR is replaced while the
     * server is running, a class generated for a BukkitRunnable can otherwise disappear halfway
     * through a match. Loading every plugin-owned class up front also makes an incomplete build
     * fail during startup instead of later in an event handler.
     */
    private fun preloadPluginClasses() {
        val source = javaClass.protectionDomain.codeSource?.location ?: return
        if (source.protocol != "file") return
        val jarFile = runCatching { java.io.File(source.toURI()) }.getOrNull() ?: return
        if (!jarFile.isFile || !jarFile.name.endsWith(".jar", ignoreCase = true)) return

        val classNames = JarFile(jarFile).use { archive ->
            archive.entries().asSequence()
                .map { it.name }
                .filter { entry ->
                    entry.startsWith(PLUGIN_CLASS_PATH) &&
                        !entry.startsWith(RELOCATED_LIBRARY_PATH) &&
                        entry.endsWith(".class")
                }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .toList()
        }
        val failures = mutableListOf<String>()
        classNames.forEach { className ->
            try {
                Class.forName(className, false, javaClass.classLoader)
            } catch (error: ClassNotFoundException) {
                failures += "$className (${error.javaClass.simpleName})"
            } catch (error: LinkageError) {
                failures += "$className (${error.javaClass.simpleName})"
            }
        }
        check(failures.isEmpty()) {
            "플러그인 런타임 클래스를 불러올 수 없습니다: ${failures.take(10).joinToString()}"
        }
        loggerInfo("런타임 클래스 ${classNames.size}개를 사전 로드했습니다.")
    }

    private fun registerEvents() {
        val command = Command()
        server.getPluginCommand("classwar")?.setExecutor(command)

        server.pluginManager.registerEvents(command, this)
        server.pluginManager.registerEvents(OnInventoryClickEvent(), this)
        server.pluginManager.registerEvents(OnInventoryCloseEvent(), this)
        server.pluginManager.registerEvents(OnPlayerDeathEvent(), this)
        server.pluginManager.registerEvents(OnEntityDamageByEntityEvent(), this)
        server.pluginManager.registerEvents(OnEntityDamageEvent(), this)
        server.pluginManager.registerEvents(OnEntityRegainHealthEvent(), this)
        server.pluginManager.registerEvents(OnEntityDeathEvent(), this)
        server.pluginManager.registerEvents(OnDamageIndicatorEvent(), this)
        server.pluginManager.registerEvents(OnPlayerInteractEvent(), this)
        server.pluginManager.registerEvents(OnFoodChangeEvent(), this)
        server.pluginManager.registerEvents(OnPlayerSkillUseEvent(), this)
        server.pluginManager.registerEvents(OnPlayerMoveEvent(), this)
        server.pluginManager.registerEvents(OnPlayerConnectionEvent(this), this)
        server.pluginManager.registerEvents(OnPlayerSwapHandItemsEvent(), this)
        server.pluginManager.registerEvents(OnPlayerToggleSneakEvent(), this)
        server.pluginManager.registerEvents(OnPlayerInputEvent(), this)
        server.pluginManager.registerEvents(OnAsyncChatEvent(), this)
        server.pluginManager.registerEvents(OnProjectileHitEvent(), this)
        server.pluginManager.registerEvents(OnEntityShootBowEvent(), this)
        server.pluginManager.registerEvents(OnBattleMapEvent(), this)
    }

    private fun startStatusActionBarTask() {
        statusActionBarTask?.cancel()
        statusActionBarTask = object : BukkitRunnable() {
            override fun run() {
                val games = buildList {
                    Info.game?.let { add(it) }
                    addAll(GameManager.trainingInstance)
                }
                if (games.isEmpty()) return
                StatusAbnormalityManager.run {
                    games.flatMap { it.playerDatas }
                        .filterIsInstance<PlayerData>()
                        .distinctBy { it.player.uniqueId }
                        .filter { it.player.isOnline }
                        .forEach { playerData -> playerData.updateStatusActionBar() }
                }
            }
        }.runTaskTimer(this, 0L, 20L)
    }

    fun loggerInfo(msg: String) {
        logger.info("[ClassWar] $msg")
    }
}
