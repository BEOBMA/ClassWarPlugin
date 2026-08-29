package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.skill.SkillContext
import org.beobma.classWarPlugin.status.list.Silence
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.status.list.Stun
import org.beobma.classWarPlugin.status.list.Enchantment
import org.beobma.classWarPlugin.status.list.Fix
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import kotlin.math.cos


object SkillManager {
    private val skillIdKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "skill-id")
    private val skillOwnerKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "skill-owner")

    fun markSkillItem(item: ItemStack, skill: Skill, ownerId: UUID): ItemStack = item.apply {
        itemMeta = itemMeta.apply {
            persistentDataContainer.set(skillIdKey, PersistentDataType.STRING, skill.id)
            persistentDataContainer.set(skillOwnerKey, PersistentDataType.STRING, ownerId.toString())
        }
    }

    fun getSkillId(item: ItemStack, ownerId: UUID): String? {
        val container = item.itemMeta.persistentDataContainer
        if (container.get(skillOwnerKey, PersistentDataType.STRING) != ownerId.toString()) return null
        return container.get(skillIdKey, PersistentDataType.STRING)
    }

    private fun EntityData.isTraining(): Boolean = when (this) {
        is PlayerData -> PlayerTagManager.hasTag(player, "isTraining")
        else -> false
    }

    fun EntityData.getTargetCandidates(): List<EntityData> {
        val candidates: MutableList<EntityData> = game.playerDatas.toMutableList()
        val sourcePlayer = this as? PlayerData
        if (sourcePlayer != null && isTraining()) {
            sourcePlayer.player.world.livingEntities
                .filter { it.uniqueId != sourcePlayer.uniqueId && it !is Player }
                .forEach { livingEntity ->
                val data = game.playerDatas.find { it.entity == livingEntity }
                    ?: if (livingEntity.isMannequin()) DummyEntityData(livingEntity, game)
                    else MobEntityData(livingEntity, game)
                if (data !in game.playerDatas) game.playerDatas.add(data)
                candidates.add(data)
            }
        }
        return candidates.distinctBy { it.entity.uniqueId }
    }

    fun EntityData.use(skill: Skill, clickedItem: ItemStack): Boolean {
        val playerData = this as? PlayerData ?: return false
        if (!entityStatus.canSkillUse) {
            playerData.player.sendMiniMessage("<red><bold>[!] 현재 스킬을 사용할 수 없는 상태입니다.")
            return false
        }
        if (playerData.hasStatus<Stun>() || playerData.hasStatus<Enchantment>()) {
            playerData.player.sendMiniMessage("<red><bold>[!] 기절 상태에서는 스킬을 사용할 수 없습니다.")
            return false
        }
        if (playerData.hasStatus<Silence>() && !skill.canUseWhileSilenced) {
            playerData.player.sendMiniMessage("<red><bold>[!] 침묵 상태에서는 스킬을 사용할 수 없습니다.")
            return false
        }
        if (CooldownManager.hasCooldown(playerData.player, skill)) {
            playerData.player.sendMiniMessage("<red><bold>[!] 재사용 대기 중입니다.")
            return false
        }
        if (skill is MovementSkill && playerData.hasStatus<Fix>()) {
            playerData.player.sendMiniMessage("<red><bold>[!] 고정 상태에서는 이동 스킬을 사용할 수 없습니다.")
            return false
        }

        val baseCooldownTicks = when (val cooldown = skill.cooldown) {
            null -> 0
            Int.MAX_VALUE -> 999999 * 20
            else -> cooldown.coerceAtLeast(0) * 20
        }

        if (!skill.isUseSuccess()) {
            return false
        }

        val context = SkillContext(playerData, skill, clickedItem, baseCooldownTicks)
        val playerSkillUseEvent = PlayerSkillUseEvent(context)
        Bukkit.getServer().pluginManager.callEvent(playerSkillUseEvent)
        if (playerSkillUseEvent.isCancelled) {
            return false
        }

        skill.execute(context)
        if (context.cooldownTicks > 0) {
            CooldownManager.setCooldown(playerData.player, skill, clickedItem, context.cooldownTicks)
        }



        return true
    }
    fun EntityData.radius(
        location: Location,
        targetType: TargetType,
        radius: Double,
        oneself: Boolean,
        hitAttackableObjects: Boolean = true,
    ): List<EntityData> {
        val effectiveRadius = ClassBalanceManager.scaleRange(this, radius)
        val isTraining = isTraining()
        val sourcePlayer = this as? PlayerData
        if (hitAttackableObjects && sourcePlayer != null && targetType == Enemy) {
            AttackableObjectManager.hitSphere(sourcePlayer.uniqueId, location, effectiveRadius)
        }
        val world = entity.world
        val nearbyEntities = world.getNearbyEntities(location, effectiveRadius, effectiveRadius, effectiveRadius)
            .filterIsInstance<LivingEntity>()
        val entityDatas = getTargetCandidates().filter { entityData ->
            val playerStatus = entityData.entityStatus
            return@filter !playerStatus.isDead && playerStatus.isSkillTargeting
        }
        val nearbyEntityData = nearbyEntities.mapNotNull { target ->
            entityDatas.find { it.entity == target }
        }.filter { candidate ->
            HitboxUtil.intersectsSphere(candidate.entity.boundingBox, location.toVector(), effectiveRadius)
        }

        return when (targetType) {
            Self -> if (oneself) nearbyEntityData.filter { it == this } else emptyList()

            Enemy -> {
                nearbyEntityData.filter { candidate ->
                    (candidate !is PlayerData && isTraining) ||
                        (sourcePlayer != null && candidate is PlayerData && sourcePlayer.isEnemyOf(candidate))
                }
            }

            All -> {
                nearbyEntityData
            }
        }
    }
    fun EntityData.shotLaserGetEntityData(maxRange: Double, targetType: TargetType, wallShot: Boolean): EntityData? {
        val sourcePlayer = this as? PlayerData ?: return null
        val isTraining = isTraining()
        val world = this.entity.world
        val playerDatas = getTargetCandidates().filter { entityData ->
            val playerStatus = entityData.entityStatus
            return@filter !playerStatus.isDead && playerStatus.isSkillTargeting && !entityData.hasStatus<Stealth>()
        }
        val entity = entity
        if (entity !is LivingEntity) return null
        val startLocation = entity.eyeLocation
        val direction = startLocation.direction

        val maxDistance = ClassBalanceManager.scaleRange(this, maxRange)

        val hitEntityData = playerDatas.asSequence()
            .filter { candidate ->
                val hitEntity = candidate.entity
                if (hitEntity === this.entity || hitEntity !is LivingEntity) return@filter false
                when (targetType) {
                    Self -> false
                    Enemy -> isTraining && candidate !is PlayerData ||
                        (candidate is PlayerData && sourcePlayer.isEnemyOf(candidate))
                    All -> true
                }
            }
            .mapNotNull { candidate ->
                HitboxUtil.rayIntersectionDistance(
                    candidate.entity.boundingBox,
                    startLocation.toVector(),
                    direction,
                    maxDistance,
                    expansion = 1.0,
                )?.let { distance -> candidate to distance }
            }
            .minByOrNull { it.second }
            ?: return null
        val hitEntity = hitEntityData.first.entity
        if (!wallShot) {
            val blockRayTraceResult = world.rayTraceBlocks(startLocation, direction, maxDistance)
            val blockPosition = blockRayTraceResult?.hitPosition
            if (blockPosition != null &&
                blockPosition.distanceSquared(startLocation.toVector()) <=
                hitEntityData.second * hitEntityData.second
            ) {
                return null
            }
        }
        if (hitEntity !is LivingEntity) return null
        val targetData = hitEntityData.first
        if (targetData.entityStatus.isSkillTargeting) {
            if (isTraining && hitEntity !is Player) {
                return targetData
            }
            val isValidTarget = when (targetType) {
                Self -> false
                Enemy -> targetData !is PlayerData || sourcePlayer.isEnemyOf(targetData)
                All -> true
            }
            if (!isValidTarget) {
                return null
            }
            return targetData
        }
        return null
    }
    fun EntityData.shotLaserGetBlock(maxRange: Double): Block? {
        val sourcePlayer = this as? PlayerData ?: return null
        val world = sourcePlayer.player.world
        val startLocation = sourcePlayer.player.eyeLocation
        val direction = startLocation.direction

        val maxDistance = ClassBalanceManager.scaleRange(this, maxRange)

        val blockRayTraceResult = world.rayTraceBlocks(startLocation, direction, maxDistance)
        return blockRayTraceResult?.hitBlock
    }
    fun EntityData.getConeTargets(radius: Double, angle: Double, targetType: TargetType, includeSelf: Boolean): List<EntityData> {
        val sourcePlayer = this as? PlayerData ?: return emptyList()
        val effectiveRadius = ClassBalanceManager.scaleRange(this, radius)
        if (targetType == Enemy) {
            AttackableObjectManager.hitCone(sourcePlayer.uniqueId, sourcePlayer.player.eyeLocation, effectiveRadius, angle)
        }
        val isTraining = isTraining()
        val playerLocation = sourcePlayer.player.location
        val playerDirection = playerLocation.direction.normalize()

        return getTargetCandidates().filter { targetPlayerData ->
            if (!targetPlayerData.entityStatus.isSkillTargeting || targetPlayerData.entityStatus.isDead)
                return@filter false

            if (!includeSelf && targetPlayerData == this)
                return@filter false

            if (!(isTraining && targetPlayerData !is PlayerData)) {
                when (targetType) {
                    Self -> if (targetPlayerData != sourcePlayer) return@filter false
                    Enemy -> if (targetPlayerData !is PlayerData || !sourcePlayer.isEnemyOf(targetPlayerData)) return@filter false
                    All -> if (targetPlayerData !is PlayerData) return@filter false
                }
            }

            val distanceSquared = HitboxUtil.distanceSquared(targetPlayerData.entity.boundingBox, playerLocation.toVector())
            if (distanceSquared > effectiveRadius * effectiveRadius) return@filter false
            if (distanceSquared == 0.0) return@filter true

            val targetPoint = HitboxUtil.closestPoint(targetPlayerData.entity.boundingBox, playerLocation.toVector())
            val directionToTarget = targetPoint.clone().subtract(playerLocation.toVector()).normalize()

            val dotProduct = playerDirection.dot(directionToTarget)
            dotProduct >= cos(Math.toRadians(angle / 2))
        }
    }

}
