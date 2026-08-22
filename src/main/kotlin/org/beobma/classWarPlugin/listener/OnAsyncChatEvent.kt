package org.beobma.classWarPlugin.listener

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.list.Hacker
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class OnAsyncChatEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        if (!Hacker.hasActiveSession(player.uniqueId)) return
        event.isCancelled = true
        val input = PlainTextComponentSerializer.plainText().serialize(event.message())
        Bukkit.getScheduler().runTask(ClassWarPlugin.instance, Runnable {
            Hacker.handleChatInput(player, input)
        })
    }
}
