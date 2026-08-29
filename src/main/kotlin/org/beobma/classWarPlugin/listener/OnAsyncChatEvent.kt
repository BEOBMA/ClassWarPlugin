package org.beobma.classWarPlugin.listener

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.gameClass.list.Hacker
import org.beobma.classWarPlugin.gameClass.list.Mathematician
import org.beobma.classWarPlugin.gameClass.list.Referee
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class OnAsyncChatEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        val isHacking = Hacker.hasActiveSession(player.uniqueId)
        val isAnsweringMath = Mathematician.hasActiveProblem(player.uniqueId)
        val isInTrial = Referee.hasActiveTrial(player.uniqueId)
        if (!isHacking && !isAnsweringMath && !isInTrial) return
        event.isCancelled = true
        val input = PlainTextComponentSerializer.plainText().serialize(event.message())
        Bukkit.getScheduler().runTask(ClassWarPlugin.instance, Runnable {
            if (isInTrial) Referee.handleChatInput(player, input)
            else if (isHacking) Hacker.handleChatInput(player, input)
            else Mathematician.handleChatInput(player, input)
        })
    }
}
