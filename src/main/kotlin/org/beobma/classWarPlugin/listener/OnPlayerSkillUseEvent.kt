package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class OnPlayerSkillUseEvent : Listener {

    @EventHandler
    fun onPlayerSkillUse(event: PlayerSkillUseEvent) {
        val playerData = event.playerData
        val skill = event.skill
        val clickedItem = event.clickedItem


    }
}