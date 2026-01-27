package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.TargetType.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.cos


object SkillManager {
    private fun PlayerData.isTraining(): Boolean =
        PlayerTagManager.hasTag(player, "isTraining") || player.isMannequin()

    private fun PlayerData.getTargetCandidates(): List<PlayerData> {
        val candidates = game.playerDatas.toMutableList()
        if (isTraining()) {
            player.world.entities.filterIsInstance<Player>().filter { it.isMannequin() }.forEach { mannequin ->
                val data = game.playerDatas.find { it.player == mannequin }
                    ?: PlayerData(mannequin, game).also { game.playerDatas.add(it) }
                candidates.add(data)
            }
        }
        return candidates.distinctBy { it.player.uniqueId }
    }

    fun PlayerData.getSkillTargetCandidates(): List<PlayerData> = getTargetCandidates()

    fun PlayerData.use(skill: Skill, clickedItem: ItemStack): Boolean {
        if (!playerStatus.canSkillUse) {
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
    fun PlayerData.radius(location: Location,targetType: TargetType, radius: Double, oneself: Boolean): List<PlayerData> {
        val isTraining = isTraining()
        val world = player.world
        val nearbyPlayers = world.getNearbyEntities(location, radius, radius, radius).filterIsInstance<Player>()
        val playerDatas = getTargetCandidates().filter { playerData ->
            val playerStatus = playerData.playerStatus
            return@filter !playerStatus.isDead && playerStatus.isSkillTargeting
        }
        val nearbyPlayerData = nearbyPlayers.mapNotNull { target ->
            playerDatas.find { it.player == target }
        }

        return when (targetType) {
            Team -> {
                if (oneself) {
                    nearbyPlayerData.filter { it.team == team || (isTraining && it.player.isMannequin()) }
                } else {
                    nearbyPlayerData.filter { it != this && (it.team == team || (isTraining && it.player.isMannequin())) }
                }
            }

            Enemy -> {
                nearbyPlayerData.filter { it.team != team || (isTraining && it.player.isMannequin()) }
            }

            All -> {
                nearbyPlayerData
            }
        }
    }
    fun PlayerData.shotLaserGetPlayerData(maxRange: Double, targetType: TargetType, wallShot: Boolean): PlayerData? {
        val isTraining = isTraining()
        val world = player.world
        val playerDatas = getTargetCandidates().filter { playerData ->
            val playerStatus = playerData.playerStatus
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

        if (entityRayTraceResult?.hitEntity is Player) {
            val hitPlayer = entityRayTraceResult.hitEntity as Player
            val hitPlayerData = playerDatas.find { it.player == hitPlayer } ?: return null
            if (hitPlayerData.playerStatus.isSkillTargeting) {
                if (isTraining && (hitPlayer.isMannequin() || player.isMannequin())) {
                    return hitPlayerData
                }
                val isValidTarget = when (targetType) {
                    Team -> hitPlayerData.team == team
                    Enemy -> hitPlayerData.team != team
                    All -> true
                }
                if (!isValidTarget) {
                    return null
                }
                return hitPlayerData
            }
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
    fun PlayerData.getConeTargets(radius: Double, angle: Double, targetType: TargetType, includeSelf: Boolean): List<PlayerData> {
        val isTraining = isTraining()
        val playerLocation = player.location
        val playerDirection = playerLocation.direction.normalize()

        return getTargetCandidates().filter { targetPlayerData ->
            if (!targetPlayerData.playerStatus.isSkillTargeting || targetPlayerData.playerStatus.isDead)
                return@filter false

            if (!includeSelf && targetPlayerData == this)
                return@filter false

            if (!(isTraining && targetPlayerData.player.isMannequin())) {
                when (targetType) {
                    Team -> if (targetPlayerData.team != team) return@filter false
                    Enemy -> if (targetPlayerData.team == team) return@filter false
                    All -> {}
                }
            }

            val targetLocation = targetPlayerData.player.location
            val directionToTarget = targetLocation.toVector().subtract(playerLocation.toVector()).normalize()

            val distanceSquared = playerLocation.distanceSquared(targetLocation)
            if (distanceSquared > radius * radius) return@filter false

            val dotProduct = playerDirection.dot(directionToTarget)
            dotProduct >= cos(Math.toRadians(angle / 2))
        }
    }


    fun createSkillItemStack(
        material: Material,
        name: String,
        lore: List<String>
    ): ItemStack {
        val nameComponent = MiniMessage.miniMessage().deserialize(UtilManager.applyKeywords(name))
        val loreComponents = lore.map { MiniMessage.miniMessage().deserialize(UtilManager.applyKeywords(it)) }
        return ItemStack(material, 1).apply {
            itemMeta = itemMeta.apply {
                displayName(nameComponent)
                lore(loreComponents)
            }
        }
    }
}
