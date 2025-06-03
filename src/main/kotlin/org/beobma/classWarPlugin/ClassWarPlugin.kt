package org.beobma.classWarPlugin

import org.beobma.classWarPlugin.command.Command
import org.beobma.classWarPlugin.listener.OnEntityDamageByEntityEvent
import org.beobma.classWarPlugin.listener.OnEntitySkillDamageByEntityEvent
import org.beobma.classWarPlugin.listener.OnInventoryClickEvent
import org.beobma.classWarPlugin.listener.OnInventoryCloseEvent
import org.beobma.classWarPlugin.listener.OnPlayerDeathEvent
import org.bukkit.plugin.java.JavaPlugin

class ClassWarPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: ClassWarPlugin
    }

    override fun onEnable() {
        instance = this

        registerEvents()
        loggerInfo("플러그인이 정상적으로 활성화되었습니다.")
    }

    override fun onDisable() {
        loggerInfo("플러그인이 정상적으로 비활성화되었습니다.")
    }

    private fun registerEvents() {
        server.getPluginCommand("classwar")?.setExecutor(Command())

        server.pluginManager.registerEvents(Command(), this)
        server.pluginManager.registerEvents(OnInventoryClickEvent(), this)
        server.pluginManager.registerEvents(OnInventoryCloseEvent(), this)
        server.pluginManager.registerEvents(OnPlayerDeathEvent(), this)
        server.pluginManager.registerEvents(OnEntityDamageByEntityEvent(), this)
        server.pluginManager.registerEvents(OnEntitySkillDamageByEntityEvent(), this)
    }

    fun loggerInfo(msg: String) {
        logger.info("[ClassWar] $msg")
    }
}
