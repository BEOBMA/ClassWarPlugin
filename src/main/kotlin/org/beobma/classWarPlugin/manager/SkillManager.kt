package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.TargetType.*
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.cos


object SkillManager {
    private fun PlayerData.isTraining(): Boolean = PlayerTagManager.hasTag(player, "isTraining")

    private fun PlayerData.getTargetCandidates(): List<EntityData> {
        val candidates: MutableList<EntityData> = initGame.playerDatas.toMutableList()
        if (isTraining()) {
            player.world.entities.filter { it.isMannequin() }.forEach { mannequin ->
                val data = initGame.playerDatas.find { it.entity == mannequin }
                    ?: DummyEntityData(mannequin, initGame).also { initGame.playerDatas.add(it) }
                candidates.add(data)
            }
        }
        return candidates.distinctBy { it.entity.uniqueId }
    }

    fun PlayerData.use(skill: Skill, clickedItem: ItemStack): Boolean {
        if (!entityStatus.canSkillUse) {
            player.sendMiniMessage("<red><bold>[!] 현재 스킬을 사용할 수 없는 상태입니다.")
            return false
        }
        if (player.hasCooldown(clickedItem.type)) {
            player.sendMiniMessage("<red><bold>[!] 재사용 대기 중입니다.")
            return false
        }

        val isUse = skill.use()
        if (!isUse) return false
        val cooldown = if (skill.cooldown == Int.MAX_VALUE) 999999 else skill.cooldown ?: return true
        player.setCooldown(clickedItem.type, cooldown * 20)
        return true
    }
    fun PlayerData.radius(location: Location, targetType: TargetType, radius: Double, oneself: Boolean): List<EntityData> {
        val isTraining = isTraining()
        val world = player.world
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
                if (oneself) {
                    nearbyEntityData.filter {
                        it.entity.isMannequin() && isTraining ||
                            (it is PlayerData && it.team == team)
                    }
                } else {
                    nearbyEntityData.filter { candidate ->
                        candidate != this &&
                            (candidate.entity.isMannequin() && isTraining ||
                                (candidate is PlayerData && candidate.team == team))
                    }
                }
            }

            Enemy -> {
                nearbyEntityData.filter { candidate ->
                    candidate.entity.isMannequin() && isTraining ||
                        (candidate is PlayerData && candidate.team != team)
                }
            }

            All -> {
                nearbyEntityData
            }
        }
    }
    fun PlayerData.shotLaserGetEntityData(maxRange: Double, targetType: TargetType, wallShot: Boolean): EntityData? {
        val isTraining = isTraining()
        val world = player.world
        val playerDatas = getTargetCandidates().filter { entityData ->
            val playerStatus = entityData.entityStatus
            return@filter !playerStatus.isDead && playerStatus.isSkillTargeting
        }
        val startLocation = player.eyeLocation
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
            entity !== player
        }

        val hitEntity = entityRayTraceResult?.hitEntity ?: return null
        if (hitEntity !is Player && !hitEntity.isMannequin()) return null
        val hitEntityData = playerDatas.find { it.entity == hitEntity } ?: return null
        if (hitEntityData.entityStatus.isSkillTargeting) {
            if (isTraining && hitEntity.isMannequin()) {
                return hitEntityData
            }
            val isValidTarget = when (targetType) {
                Team -> hitEntityData is PlayerData && hitEntityData.team == team
                Enemy -> hitEntityData is PlayerData && hitEntityData.team != team
                All -> hitEntityData is PlayerData
            }
            if (!isValidTarget) {
                return null
            }
            return hitEntityData
        }
        return null
    }
    fun PlayerData.shotLaserGetBlock(maxRange: Double): Block? {
        val world = player.world
        val startLocation = player.eyeLocation
        val direction = startLocation.direction

        val maxDistance: Double = maxRange

        val blockRayTraceResult = world.rayTraceBlocks(startLocation, direction, maxDistance)
        return blockRayTraceResult?.hitBlock
    }
    fun PlayerData.getConeTargets(radius: Double, angle: Double, targetType: TargetType, includeSelf: Boolean): List<EntityData> {
        val isTraining = isTraining()
        val playerLocation = player.location
        val playerDirection = playerLocation.direction.normalize()

        return getTargetCandidates().filter { targetPlayerData ->
            if (!targetPlayerData.entityStatus.isSkillTargeting || targetPlayerData.entityStatus.isDead)
                return@filter false

            if (!includeSelf && targetPlayerData == this)
                return@filter false

            if (!(isTraining && targetPlayerData.entity.isMannequin())) {
                when (targetType) {
                    Team -> if (targetPlayerData !is PlayerData || targetPlayerData.team != team) return@filter false
                    Enemy -> if (targetPlayerData !is PlayerData || targetPlayerData.team == team) return@filter false
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
