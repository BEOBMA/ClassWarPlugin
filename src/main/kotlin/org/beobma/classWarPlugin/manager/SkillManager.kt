package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.TargetType.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.cos


object SkillManager {
    private fun EntityData.isTraining(): Boolean = when (this) {
        is PlayerData -> PlayerTagManager.hasTag(player, "isTraining")
        else -> false
    }

    private fun EntityData.getTargetCandidates(): List<EntityData> {
        val candidates: MutableList<EntityData> = game.playerDatas.toMutableList()
        val sourcePlayer = this as? PlayerData
        if (sourcePlayer != null && isTraining()) {
            sourcePlayer.player.world.entities.filter { it.isMannequin() }.forEach { mannequin ->
                val data = game.playerDatas.find { it.entity == mannequin }
                    ?: DummyEntityData(mannequin, game).also { game.playerDatas.add(it) }
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
        if (playerData.player.hasCooldown(clickedItem.type)) {
            playerData.player.sendMiniMessage("<red><bold>[!] 재사용 대기 중입니다.")
            return false
        }

        val cooldownSeconds = when (val cooldown = skill.cooldown) {
            null -> null
            Int.MAX_VALUE -> 999999
            else -> cooldown
        }

        val playerSkillUseEvent = PlayerSkillUseEvent(playerData, skill, clickedItem)
        Bukkit.getServer().pluginManager.callEvent(playerSkillUseEvent)
        if (playerSkillUseEvent.isCancelled) {
            return false
        }

        val isUse = skill.use()
        if (!isUse) {
            return false
        }

        if (cooldownSeconds != null) {
            playerData.player.setCooldown(clickedItem.type, cooldownSeconds * 20)
        }



        return true
    }
    fun EntityData.radius(location: Location, targetType: TargetType, radius: Double, oneself: Boolean): List<EntityData> {
        val isTraining = isTraining()
        val sourcePlayer = this as? PlayerData
        val world = entity.world
        val nearbyEntities = world.getNearbyEntities(location, radius, radius, radius)
            .filter { it is Player || it.isMannequin() }
        val entityDatas = getTargetCandidates().filter { entityData ->
            val playerStatus = entityData.entityStatus
            return@filter !playerStatus.isDead && playerStatus.isSkillTargeting
        }
        val nearbyEntityData = nearbyEntities.mapNotNull { target ->
            entityDatas.find { it.entity == target }
        }

        return when (targetType) {
            Team -> {
                val team = sourcePlayer?.team
                if (oneself) {
                    nearbyEntityData.filter {
                        it.entity.isMannequin() && isTraining ||
                            (team != null && it is PlayerData && it.team == team)
                    }
                } else {
                    nearbyEntityData.filter { candidate ->
                        candidate != this &&
                            (candidate.entity.isMannequin() && isTraining ||
                                (team != null && candidate is PlayerData && candidate.team == team))
                    }
                }
            }

            Enemy -> {
                val team = sourcePlayer?.team
                nearbyEntityData.filter { candidate ->
                    candidate.entity.isMannequin() && isTraining ||
                        (team != null && candidate is PlayerData && candidate.team != team)
                }
            }

            All -> {
                nearbyEntityData
            }
        }
    }
    fun EntityData.shotLaserGetEntityData(maxRange: Double, targetType: TargetType, wallShot: Boolean): EntityData? {
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

        val maxDistance: Double = maxRange

        val blockRayTraceResult = world.rayTraceBlocks(startLocation, direction, maxDistance)

        if (wallShot) {
            if (blockRayTraceResult?.hitBlock != null) {
                if (blockRayTraceResult.hitBlock!!.isSolid) {
                    return null
                }
            }
        }

        val entityRayTraceResult = world.rayTraceEntities(startLocation, direction, maxDistance, 1.0) { entity ->
            entity !== this.entity
        }

        val hitEntity = entityRayTraceResult?.hitEntity ?: return null
        if (hitEntity !is Player && !hitEntity.isMannequin()) return null
        val hitEntityData = playerDatas.find { it.entity == hitEntity } ?: return null
        if (hitEntityData.entityStatus.isSkillTargeting) {
            if (isTraining && hitEntity.isMannequin()) {
                return hitEntityData
            }
            val isValidTarget = when (targetType) {
                Team -> hitEntityData is PlayerData && hitEntityData.team == this.team
                Enemy -> hitEntityData is PlayerData && hitEntityData.team != this.team
                All -> hitEntityData is PlayerData
            }
            if (!isValidTarget) {
                return null
            }
            return hitEntityData
        }
        return null
    }
    fun EntityData.shotLaserGetBlock(maxRange: Double): Block? {
        val sourcePlayer = this as? PlayerData ?: return null
        val world = sourcePlayer.player.world
        val startLocation = sourcePlayer.player.eyeLocation
        val direction = startLocation.direction

        val maxDistance: Double = maxRange

        val blockRayTraceResult = world.rayTraceBlocks(startLocation, direction, maxDistance)
        return blockRayTraceResult?.hitBlock
    }
    fun EntityData.getConeTargets(radius: Double, angle: Double, targetType: TargetType, includeSelf: Boolean): List<EntityData> {
        val sourcePlayer = this as? PlayerData ?: return emptyList()
        val isTraining = isTraining()
        val playerLocation = sourcePlayer.player.location
        val playerDirection = playerLocation.direction.normalize()

        return getTargetCandidates().filter { targetPlayerData ->
            if (!targetPlayerData.entityStatus.isSkillTargeting || targetPlayerData.entityStatus.isDead)
                return@filter false

            if (!includeSelf && targetPlayerData == this)
                return@filter false

            if (!(isTraining && targetPlayerData.entity.isMannequin())) {
                when (targetType) {
                    Team -> if (targetPlayerData !is PlayerData || targetPlayerData.team != sourcePlayer.team) return@filter false
                    Enemy -> if (targetPlayerData !is PlayerData || targetPlayerData.team == sourcePlayer.team) return@filter false
                    All -> if (targetPlayerData !is PlayerData) return@filter false
                }
            }

            val targetLocation = targetPlayerData.entity.location
            val directionToTarget = targetLocation.toVector().subtract(playerLocation.toVector()).normalize()

            val distanceSquared = playerLocation.distanceSquared(targetLocation)
            if (distanceSquared > radius * radius) return@filter false

            val dotProduct = playerDirection.dot(directionToTarget)
            dotProduct >= cos(Math.toRadians(angle / 2))
        }
    }
}
