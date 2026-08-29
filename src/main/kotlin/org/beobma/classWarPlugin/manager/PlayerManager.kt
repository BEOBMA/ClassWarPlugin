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
import org.beobma.classWarPlugin.gameClass.list.Reverse
import org.beobma.classWarPlugin.manager.GameClassManager.toWeaponItemStack
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.SkillManager.markSkillItem
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.InventoryManager.skillDyeMaterial
import org.beobma.classWarPlugin.manager.ItemDescriptionManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.util.DamageCalculator
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.DamageType.StatusAbnormality
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object PlayerManager {
    private val miniMessage = MiniMessage.miniMessage()
    private const val invincibilityTicks: Long = 20
    private val lastDamageTicks: MutableMap<DamageInvincibilityKey, Long> = mutableMapOf()
    private val passiveIdKey: NamespacedKey
        get() = NamespacedKey(org.beobma.classWarPlugin.ClassWarPlugin.instance, "passive-id")
    private val passiveOwnerKey: NamespacedKey
        get() = NamespacedKey(org.beobma.classWarPlugin.ClassWarPlugin.instance, "passive-owner")

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
        val assignedClasses = gameClasses.toList()
        if (assignedClasses.isEmpty()) return
        assignedClasses.forEach { gameClass ->
            gameClass.inject(this)
            gameClass.skills.forEach { skill -> skill.inject(this) }
            gameClass.passives.forEach { passive -> passive.inject(this) }
        }

        player.inventory.setHelmet(ItemStack(Material.IRON_HELMET))
        player.inventory.setChestplate(ItemStack(Material.IRON_CHESTPLATE))
        player.inventory.setLeggings(ItemStack(Material.IRON_LEGGINGS))
        player.inventory.setBoots(ItemStack(Material.IRON_BOOTS))
        player.inventory.setItem(0, assignedClasses.first().toWeaponItemStack(player))
        if (assignedClasses.size > 1) {
            player.inventory.setItem(8, assignedClasses[1].toWeaponItemStack(player))
        }

        val hotbarSkillSlots = (1..if (assignedClasses.size > 1) 7 else 8).iterator()
        val inventorySlots = (9..35).iterator()
        val allSkills = assignedClasses.flatMap { it.skills }
        allSkills.forEachIndexed { index, skill ->
            val slot = when {
                hotbarSkillSlots.hasNext() -> hotbarSkillSlots.next()
                inventorySlots.hasNext() -> inventorySlots.next()
                else -> return@forEachIndexed
            }
            val name = UtilManager.applyKeywords(skill.name)
            val type = skillDyeMaterial(index)
            val displayItem = ItemStack(type, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(name))
                }
            }
            val item = markSkillItem(
                ItemDescriptionManager.applyForPlayer(
                    displayItem,
                    player,
                    skill.description,
                    skill.briefDescription,
                    ItemDescriptionManager.cooldownLines(skill.cooldown),
                ),
                skill,
                player.uniqueId,
            )
            player.inventory.setItem(slot, item)
        }

        assignedClasses.flatMap { it.passives }.forEach { passive ->
            if (!inventorySlots.hasNext()) return@forEach
            val name = UtilManager.applyKeywords(passive.name)
            val type = Material.WHITE_DYE
            val item = markPassiveItem(ItemDescriptionManager.applyForPlayer(ItemStack(type, 1).apply {
                itemMeta = itemMeta.apply {
                    displayName(miniMessage.deserialize(name))
                }
            }, player, passive.description, passive.briefDescription), passive, player.uniqueId)
            player.inventory.setItem(inventorySlots.next(), item)
        }

        assignedClasses.flatMap { it.extraItemMaterials }.forEach { item ->
            if (!inventorySlots.hasNext()) return@forEach
            player.inventory.setItem(inventorySlots.next(), item)
        }

        if (initializeHandlers) {
            assignedClasses.forEach { gameClass ->
                gameClass.passives.filterIsInstance<GameStatusHandler>()
                    .forEach { it.onBattleStart() }
                (gameClass as? GameStatusHandler)?.onBattleStart()
            }
        }

        if (initGame.phase == GamePhase.SCATTERING || initGame.phase == GamePhase.RUNNING) {
            BattleMapManager.giveTo(this)
        }
    }

    private fun markPassiveItem(
        item: ItemStack,
        passive: org.beobma.classWarPlugin.skill.Passive,
        ownerId: java.util.UUID,
    ): ItemStack = item.apply {
        itemMeta = itemMeta.apply {
            persistentDataContainer.set(passiveIdKey, PersistentDataType.STRING, passive.javaClass.name)
            persistentDataContainer.set(passiveOwnerKey, PersistentDataType.STRING, ownerId.toString())
        }
    }

    private fun getPassiveId(item: ItemStack, ownerId: java.util.UUID): String? {
        val container = item.itemMeta.persistentDataContainer
        if (container.get(passiveOwnerKey, PersistentDataType.STRING) != ownerId.toString()) return null
        return container.get(passiveIdKey, PersistentDataType.STRING)
    }

    /** 개인 설명 설정을 바꾼 즉시 현재 소지 중인 클래스 아이템의 설명도 갱신한다. */
    fun refreshClassItemDescriptions(playerData: PlayerData) {
        val player = playerData.player
        val registeredClasses by lazy { GameManager.gameClassList }
        fun allClasses() = sequence {
            yieldAll(playerData.gameClasses)
            yieldAll(registeredClasses)
        }

        player.inventory.contents.forEach { item ->
            if (item == null || item.type.isAir) return@forEach

            val weaponClassId = getWeaponClassId(item)
            if (weaponClassId != null) {
                val gameClass = allClasses().firstOrNull { it.javaClass.name == weaponClassId } ?: return@forEach
                ItemDescriptionManager.applyForPlayer(
                    item, player, gameClass.weapon.description, gameClass.weapon.briefDescription,
                )
                return@forEach
            }

            val skillId = getSkillId(item, player.uniqueId)
            if (skillId != null) {
                val skill = allClasses().flatMap { it.skills.asSequence() }.firstOrNull { it.id == skillId }
                    ?: return@forEach
                ItemDescriptionManager.applyForPlayer(
                    item, player, skill.description, skill.briefDescription,
                    ItemDescriptionManager.cooldownLines(skill.cooldown),
                )
                return@forEach
            }

            val passiveId = getPassiveId(item, player.uniqueId) ?: return@forEach
            val passive = allClasses().flatMap { it.passives.asSequence() }
                .firstOrNull { it.javaClass.name == passiveId } ?: return@forEach
            ItemDescriptionManager.applyForPlayer(
                item, player, passive.description, passive.briefDescription,
            )
        }
        player.updateInventory()
    }

    fun PlayerData.damage(
        damage: Double,
        damageType: DamageType,
        damager: PlayerData,
        isInvincibilityTimeIgnore: Boolean = true,
        bypassShield: Boolean = false,
        damagePath: DamagePath? = null,
        armorIgnoreRatio: Double = 0.0,
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
        val context = DamageContext(damager, this, path, damageType, damage, bypassShield, armorIgnoreRatio)
        if (!DamageManager.process(context)) return

        val damageResult = DamageCalculator.calculate(context.damage, player, damageType, context.armorIgnoreRatio)
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
        val finalDamage = ClassBalanceManager.scaleHealing(healer, damage)

        if (finalDamage < 0) {
            return
        }
        if (Reverse.invertHealingIfNeeded(this, finalDamage)) return
        player.heal(finalDamage)
    }

    fun EntityData.damage(
        damage: Double,
        damageType: DamageType,
        damager: PlayerData,
        isInvincibilityTimeIgnore: Boolean = true,
        bypassShield: Boolean = false,
        damagePath: DamagePath? = null,
        armorIgnoreRatio: Double = 0.0,
    ) {
        when (this) {
            is PlayerData -> this.damage(
                damage, damageType, damager, isInvincibilityTimeIgnore, bypassShield, damagePath, armorIgnoreRatio,
            )
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
                val context = DamageContext(damager, this, path, damageType, damage, bypassShield, armorIgnoreRatio)
                if (!DamageManager.process(context)) return
                val damageResult = targetPlayer?.let {
                    DamageCalculator.calculate(context.damage, it, damageType, context.armorIgnoreRatio)
                }
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
                val context = DamageContext(damager, this, path, damageType, damage, bypassShield, armorIgnoreRatio)
                if (!DamageManager.process(context)) return
                val target = entity
                val result = DamageCalculator.calculate(
                    context.damage, target, damageType, context.armorIgnoreRatio,
                )
                if (result.finalDamage <= 0.0) return
                DamageIndicatorManager.show(target, result.finalDamage, game.settings.damageIndicatorsEnabled)
                target.playHurtAnimation(0.0f)
                DamageManager.recordSuccessfulDamage(context)
                target.health = (target.health - result.finalDamage).coerceAtLeast(0.0)
            }
        }
    }

    fun EntityData.heal(damage: Double, damageType: DamageType, healer: PlayerData) {
        if (Reverse.invertHealingIfNeeded(this, damage)) return
        when (this) {
            is PlayerData -> this.heal(damage, damageType, healer)
            is DummyEntityData -> return
            is MobEntityData -> entity.heal(ClassBalanceManager.scaleHealing(healer, damage))
        }
    }
}
