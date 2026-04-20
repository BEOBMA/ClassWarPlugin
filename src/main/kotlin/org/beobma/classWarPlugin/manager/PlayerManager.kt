package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.event.PlayerStatusEffectDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.manager.GameClassManager.toItemStack
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.util.DamageCalculator
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.DamageType.StatusAbnormality
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object PlayerManager {
    private val miniMessage = MiniMessage.miniMessage()
    private const val invincibilityTicks: Long = 20
    private val lastDamageTicks: MutableMap<DamageInvincibilityKey, Long> = mutableMapOf()

    private data class DamageInvincibilityKey(
        val targetId: java.util.UUID,
        val damagerId: java.util.UUID,
        val damageType: DamageType,
    )

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
            val name = UtilManager.applyKeywords(skill.name)
            val lore = skill.description.map { miniMessage.deserialize(UtilManager.applyKeywords(it)) }
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
            val name = UtilManager.applyKeywords(skill.name)
            val lore = skill.description.map { miniMessage.deserialize(UtilManager.applyKeywords(it)) }
            val type = Material.WHITE_DYE
            val item = ItemStack(type, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(name))
                    lore(lore)
                }
            }

            if (index + 9 > 26) return
            player.inventory.setItem(9 + index, item)
        }

        gameClass.extraItemMaterials.forEachIndexed { index, item ->
            if (index + 27 > 35) return
            player.inventory.setItem(index + 9, item)
        }

        gameClass.passives.forEach { passive ->
            if (passive is GameStatusHandler) {
                passive.onBattleStart()
            }
        }

        if (gameClass is GameStatusHandler) {
            gameClass.onBattleStart()
        }
    }

    fun PlayerData.damage(
        damage: Double,
        damageType: DamageType,
        damager: PlayerData,
        isInvincibilityTimeIgnore: Boolean = true
    ) {
        if (damage <= 0.0) {
            return
        }

        val currentTick = player.world.fullTime

        if (!isInvincibilityTimeIgnore) {
            val key = DamageInvincibilityKey(player.uniqueId, damager.player.uniqueId, damageType)
            lastDamageTicks[key]?.let { lastTick ->
                if (currentTick - lastTick < invincibilityTicks) {
                    return
                }
            }
            lastDamageTicks[key] = currentTick
        }

        val finalDamage = if (damageType == StatusAbnormality) {
            val event = PlayerStatusEffectDamageByPlayerEvent(damage, damageType, this, damager)
            Bukkit.getServer().pluginManager.callEvent(event)
            if (event.isCancelled) {
                return
            }
            event.damage
        } else {
            val event = PlayerSkillDamageByPlayerEvent(damage, damageType, this, damager)
            Bukkit.getServer().pluginManager.callEvent(event)
            if (event.isCancelled) {
                return
            }
            event.damage
        }
        if (finalDamage <= 0.0) {
            return
        }

        val damageResult = DamageCalculator.calculate(finalDamage, player, damageType)
        if (damageResult.finalDamage <= 0.0) {
            return
        }
        if (PlayerTagManager.hasTag(player, "isTraining")) {
            val formattedDamage = String.format("%.2f", damageResult.finalDamage)
            player.sendMiniMessage("<red>받은 피해 정보 - <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
            return
        }

        val newHealth = (player.health - damageResult.finalDamage).coerceAtLeast(0.0)
        player.health = newHealth
    }

    fun PlayerData.heal(damage: Double, damageType: DamageType, healer: PlayerData) {
        var finalDamage = damage

        if (finalDamage < 0) {
            return
        }
        player.heal(finalDamage)
    }

    fun EntityData.damage(
        damage: Double,
        damageType: DamageType,
        damager: PlayerData,
        isInvincibilityTimeIgnore: Boolean = true
    ) {
        when (this) {
            is PlayerData -> this.damage(damage, damageType, damager, isInvincibilityTimeIgnore)
            is DummyEntityData -> {
                if (damage <= 0.0) {
                    return
                }

                val targetPlayer = entity as? Player
                val currentTick = targetPlayer?.world?.fullTime ?: return
                if (!isInvincibilityTimeIgnore) {
                    val key = DamageInvincibilityKey(entity.uniqueId, damager.player.uniqueId, damageType)
                    lastDamageTicks[key]?.let { lastTick ->
                        if (currentTick - lastTick < invincibilityTicks) {
                            return
                        }
                    }
                    lastDamageTicks[key] = currentTick
                }

                val finalDamage = if (damageType == StatusAbnormality) {
                    val event = PlayerStatusEffectDamageByPlayerEvent(damage, damageType, damager, this)
                    Bukkit.getServer().pluginManager.callEvent(event)
                    if (event.isCancelled) {
                        0.0
                    } else {
                        event.damage
                    }
                } else {
                    val event = PlayerSkillDamageByPlayerEvent(damage, damageType, damager, this)
                    Bukkit.getServer().pluginManager.callEvent(event)
                    if (event.isCancelled) {
                        0.0
                    } else {
                        event.damage
                    }
                }

                val damageResult = targetPlayer?.let { DamageCalculator.calculate(finalDamage, it, damageType) }
                    ?: DamageCalculator.Result(finalDamage, 0.0)
                if (damageResult.finalDamage <= 0.0) {
                    return
                }
            }
        }
    }

    fun EntityData.heal(damage: Double, damageType: DamageType, healer: PlayerData) {
        when (this) {
            is PlayerData -> this.heal(damage, damageType, healer)
            is DummyEntityData -> return
        }
    }
}
