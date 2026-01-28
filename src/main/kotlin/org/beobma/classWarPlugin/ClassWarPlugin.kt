package org.beobma.classWarPlugin

import org.beobma.classWarPlugin.command.Command
import org.beobma.classWarPlugin.info.Info
import org.beobma.classWarPlugin.listener.OnEntityDamageByEntityEvent
import org.beobma.classWarPlugin.listener.OnEntityDamageEvent
import org.beobma.classWarPlugin.listener.OnEntitySkillDamageByEntityEvent
import org.beobma.classWarPlugin.listener.OnEntityStatusEffectDamageByEntityEvent
import org.beobma.classWarPlugin.listener.OnFoodChangeEvent
import org.beobma.classWarPlugin.listener.OnInventoryClickEvent
import org.beobma.classWarPlugin.listener.OnInventoryCloseEvent
import org.beobma.classWarPlugin.listener.OnPlayerDeathEvent
import org.beobma.classWarPlugin.listener.OnPlayerItemHeldEvent
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class ClassWarPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: ClassWarPlugin
    }

    private var statusActionBarTask: BukkitTask? = null

    override fun onEnable() {
        instance = this

        registerEvents()
        startStatusActionBarTask()
        loggerInfo("플러그인이 정상적으로 활성화되었습니다.")
    }

    override fun onDisable() {
        statusActionBarTask?.cancel()
        loggerInfo("플러그인이 정상적으로 비활성화되었습니다.")
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
        server.pluginManager.registerEvents(OnEntitySkillDamageByEntityEvent(), this)
        server.pluginManager.registerEvents(OnEntityStatusEffectDamageByEntityEvent(), this)
        server.pluginManager.registerEvents(OnPlayerItemHeldEvent(), this)
        server.pluginManager.registerEvents(OnFoodChangeEvent(), this)
    }

    private fun startStatusActionBarTask() {
        statusActionBarTask?.cancel()
        statusActionBarTask = object : BukkitRunnable() {
            override fun run() {
                val game = Info.game ?: return
                StatusAbnormalityManager.run {
                    game.playerDatas.filterIsInstance<PlayerData>()
                        .forEach { playerData -> playerData.updateStatusActionBar() }
                }
            }
        }.runTaskTimer(this, 0L, 20L)
    }

    fun loggerInfo(msg: String) {
        logger.info("[ClassWar] $msg")
    }
}
