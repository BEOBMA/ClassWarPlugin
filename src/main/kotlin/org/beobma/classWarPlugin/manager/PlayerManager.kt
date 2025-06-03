package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.GameStatusHandler
import org.beobma.classWarPlugin.manager.GameClassManager.toItemStack
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object PlayerManager {
    private val miniMessage = MiniMessage.miniMessage()
    private val lastDamageTicks: MutableMap<PlayerData, Long> = mutableMapOf()

    fun PlayerData.classSet() {
        val gameClass = gameClass ?: return
        gameClass.inject(this)
        gameClass.skills.forEach { skill ->
            skill.inject(this)
        }
        gameClass.passives.forEach { passive ->
            passive.inject(this)
        }

        player.inventory.setItem(0, gameClass.weapon.toItemStack())
        gameClass.skills.forEachIndexed { index, skill ->
            if (index + 1 > (8 - gameClass.passives.size)) return
            val name = skill.name
            val lore = skill.description.map { miniMessage.deserialize(it) }
            val type = when (index) {
                0 -> Material.RED_DYE
                1 -> Material.ORANGE_DYE
                2 -> Material.YELLOW_DYE
                else -> Material.RED_DYE
            }
            val item = ItemStack(type, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(name))
                    lore(lore)
                }
            }


            player.inventory.setItem(index + 1, item)
        }

        gameClass.passives.forEachIndexed { index, skill ->
            if (8 - index > (gameClass.skills.size + 1)) return
            val name = skill.name
            val lore = skill.description.map { miniMessage.deserialize(it) }
            val type = Material.WHITE_DYE
            val item = ItemStack(type, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(name))
                    lore(lore)
                }
            }


            player.inventory.setItem(index + 1, item)
            player.inventory.setItem(8 - index, item)
        }

        gameClass.extraItemMaterials.forEachIndexed { index, item ->
            if (index + 9 > 35) return
            player.inventory.setItem(index + 9, item)
        }

        gameClass.passives.forEach { passive ->
            if (passive is GameStatusHandler) {
                passive.onBattleStart()
            }
        }
    }

    fun PlayerData.damage(damage: Double, damageType: DamageType, damager: PlayerData, isInvincibilityTimeIgnore: Boolean = true) {
        val currentTick = player.world.fullTime

        lastDamageTicks[damager]?.let { lastTick ->
            if (currentTick - lastTick < 20) {
                return
            }
        }
        lastDamageTicks[damager] = currentTick

        var finalDamage = damage
        val event = PlayerSkillDamageByPlayerEvent(finalDamage, damageType, this, damager)
        Bukkit.getServer().pluginManager.callEvent(event)

        if (event.isCancelled) {
            return
        }
        if (damageType != DamageType.True && damageType != DamageType.StatusAbnormality) {
            finalDamage = event.damage
        }
        if (finalDamage < 0) {
            return
        }
        player.damage(0.1, null)
        player.health -= finalDamage
    }

    fun PlayerData.heal(damage: Double, damageType: DamageType, healer: PlayerData) {
        var finalDamage = damage

        if (finalDamage < 0) {
            return
        }
        player.heal(finalDamage.toDouble())
    }
}