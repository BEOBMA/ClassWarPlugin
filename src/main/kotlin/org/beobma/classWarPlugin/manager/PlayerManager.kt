package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.list.Referee
import org.beobma.classWarPlugin.manager.GameClassManager.toItemStack
import org.beobma.classWarPlugin.manager.SkillManager.markSkillItem
import org.beobma.classWarPlugin.manager.InventoryManager.skillDyeMaterial
import org.beobma.classWarPlugin.manager.ItemDescriptionManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.util.DamageCalculator
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.DamageType.StatusAbnormality
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.LivingEntity
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

    fun clearDamageInvincibility(playerIds: Collection<java.util.UUID>) {
        if (playerIds.isEmpty()) return
        lastDamageTicks.keys.removeIf { it.targetId in playerIds || it.damagerId in playerIds }
    }

    fun PlayerData.classSet(initializeHandlers: Boolean = true) {
        val gameClass = gameClass ?: return
        gameClass.inject(this)
        gameClass.skills.forEach { skill ->
            skill.inject(this)
        }
        gameClass.passives.forEach { passive ->
            passive.inject(this)
        }

        player.inventory.setHelmet(ItemStack(Material.IRON_HELMET))
        player.inventory.setChestplate(ItemStack(Material.IRON_CHESTPLATE))
        player.inventory.setLeggings(ItemStack(Material.IRON_LEGGINGS))
        player.inventory.setBoots(ItemStack(Material.IRON_BOOTS))
        player.inventory.setItem(0, gameClass.weapon.toItemStack())
        gameClass.skills.forEachIndexed { index, skill ->
            if (index + 1 > 8) return@forEachIndexed
            val name = UtilManager.applyKeywords(skill.name)
            val type = skillDyeMaterial(index)
            val displayItem = ItemStack(type, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(name))
                }
            }
            val item = markSkillItem(
                ItemDescriptionManager.apply(
                    displayItem,
                    skill.description,
                    ItemDescriptionManager.cooldownLines(skill.cooldown),
                ),
                skill,
                player.uniqueId,
            )


            player.inventory.setItem(index + 1, item)
        }

        gameClass.passives.forEachIndexed { index, skill ->
            val name = UtilManager.applyKeywords(skill.name)
            val type = Material.WHITE_DYE
            val item = ItemDescriptionManager.apply(ItemStack(type, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(name))
                }
            }, skill.description)

            if (index + 9 > 26) return@forEachIndexed
            player.inventory.setItem(9 + index, item)
        }

        gameClass.extraItemMaterials.forEachIndexed { index, item ->
            if (index + 27 > 35) return@forEachIndexed
            player.inventory.setItem(index + 27, item)
        }

        if (initializeHandlers) {
            gameClass.passives.forEach { passive ->
                if (passive is GameStatusHandler) {
                    passive.onBattleStart()
                }
            }

            if (gameClass is GameStatusHandler) {
                gameClass.onBattleStart()
            }
        }

        if (initGame.phase == GamePhase.SCATTERING || initGame.phase == GamePhase.RUNNING) {
            BattleMapManager.giveTo(this)
        }
    }

    fun PlayerData.damage(
        damage: Double,
        damageType: DamageType,
        damager: PlayerData,
        isInvincibilityTimeIgnore: Boolean = true,
        bypassShield: Boolean = false,
        damagePath: DamagePath? = null,
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

        val path = damagePath ?: if (damageType == StatusAbnormality) DamagePath.STATUS_EFFECT else DamagePath.SKILL
        val context = DamageContext(damager, this, path, damageType, damage, bypassShield)
        if (!DamageManager.process(context)) return

        val damageResult = DamageCalculator.calculate(context.damage, player, damageType)
        if (damageResult.finalDamage <= 0.0) {
            return
        }
        DamageIndicatorManager.show(player, damageResult.finalDamage, initGame.settings.damageIndicatorsEnabled)
        player.playHurtAnimation(0.0f)
        if (PlayerTagManager.hasTag(player, "isTraining")) {
            val formattedDamage = String.format("%.2f", damageResult.finalDamage)
            player.sendMiniMessage("<red>받은 피해 정보 - <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
            return
        }

        DamageManager.recordSuccessfulDamage(context)
        Referee.recordDamage(context, damageResult.finalDamage)
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
        isInvincibilityTimeIgnore: Boolean = true,
        bypassShield: Boolean = false,
        damagePath: DamagePath? = null,
    ) {
        when (this) {
            is PlayerData -> this.damage(damage, damageType, damager, isInvincibilityTimeIgnore, bypassShield, damagePath)
            is DummyEntityData -> {
                if (damage <= 0.0) {
                    return
                }

                val targetPlayer = entity as? Player
                val currentTick = entity.world.fullTime
                if (!isInvincibilityTimeIgnore) {
                    val key = DamageInvincibilityKey(entity.uniqueId, damager.player.uniqueId, damageType)
                    lastDamageTicks[key]?.let { lastTick ->
                        if (currentTick - lastTick < invincibilityTicks) {
                            return
                        }
                    }
                    lastDamageTicks[key] = currentTick
                }

                val path = damagePath ?: if (damageType == StatusAbnormality) DamagePath.STATUS_EFFECT else DamagePath.SKILL
                val context = DamageContext(damager, this, path, damageType, damage, bypassShield)
                if (!DamageManager.process(context)) return
                val damageResult = targetPlayer?.let { DamageCalculator.calculate(context.damage, it, damageType) }
                    ?: DamageCalculator.Result(context.damage, 0.0)
                if (damageResult.finalDamage <= 0.0) {
                    return
                }
                val formattedDamage = String.format("%.2f", damageResult.finalDamage)
                (entity as? LivingEntity)?.playHurtAnimation(0.0f)
                damager.player.sendMiniMessage(
                    "<gray>피해 경로: ${path.displayName} <gray>피해량: <gold><bold>$formattedDamage</bold></gold>"
                )
            }
            is MobEntityData -> {
                if (damage <= 0.0 || entity.isDead) return
                val currentTick = entity.world.fullTime
                if (!isInvincibilityTimeIgnore) {
                    val key = DamageInvincibilityKey(entity.uniqueId, damager.player.uniqueId, damageType)
                    lastDamageTicks[key]?.let { lastTick ->
                        if (currentTick - lastTick < invincibilityTicks) return
                    }
                    lastDamageTicks[key] = currentTick
                }
                val path = damagePath ?: if (damageType == StatusAbnormality) DamagePath.STATUS_EFFECT else DamagePath.SKILL
                val context = DamageContext(damager, this, path, damageType, damage, bypassShield)
                if (!DamageManager.process(context)) return
                val target = entity
                val result = DamageCalculator.calculate(context.damage, target, damageType)
                if (result.finalDamage <= 0.0) return
                DamageIndicatorManager.show(target, result.finalDamage, game.settings.damageIndicatorsEnabled)
                target.playHurtAnimation(0.0f)
                DamageManager.recordSuccessfulDamage(context)
                target.health = (target.health - result.finalDamage).coerceAtLeast(0.0)
            }
        }
    }

    fun EntityData.heal(damage: Double, damageType: DamageType, healer: PlayerData) {
        when (this) {
            is PlayerData -> this.heal(damage, damageType, healer)
            is DummyEntityData -> return
            is MobEntityData -> entity.heal(damage)
        }
    }
}
