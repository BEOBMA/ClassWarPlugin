package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.inventory.ItemStack

interface OnSkillUseHandler {
    fun onSkillUse(playerData: PlayerData, skill: Skill, clickedItem: ItemStack)
}