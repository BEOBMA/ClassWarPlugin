package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.player.PlayerData
import org.beobma.classWarPlugin.skill.Meteor
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.util.TargetType.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.cos


object SkillManager {
    fun PlayerData.use(skill: Skill, clickedItem: ItemStack) {
        val skillItem = clickedItem
        if (!playerStatus.canSkillUse) return
        if (player.hasCooldown(skillItem.type)) return

        val isUse = skill.use()
        if (!isUse) return
        val cooldown = skill.cooldown
        if (cooldown == null) return
        player.setCooldown(clickedItem.type, cooldown * 20)
    }
    fun PlayerData.radius(location: Location,targetType: TargetType, radius: Double, oneself: Boolean): List<PlayerData> {
        val game = game
        val world = player.world
        val playerLocation = location
        val nearbyEntities = world.getNearbyEntities(playerLocation, radius, radius, radius).filter { it is Player }
        val playerDatas = game.playerDatas.filter { playerData ->
            val playerStatus = playerData.playerStatus
            return@filter !playerStatus.isDead && playerStatus.isSkillTargeting
        }
        val nearbyPlayerData = nearbyEntities.map { playerDatas.find { it.player == it } ?: return listOf() }

        return when (targetType) {
            Team -> {
                if (oneself) nearbyPlayerData.filter { it.team == team } else nearbyPlayerData.filter { it.team == team && it != this }
            }

            Enemy -> {
                nearbyPlayerData.filter { it.team != team }
            }

            All -> {
                nearbyPlayerData
            }
        }
    }
    fun PlayerData.shotLaserGetPlayerData(maxRange: Double, targetType: TargetType, wallShot: Boolean): PlayerData? {
        val game = game
        val world = player.world
        val playerDatas = game.playerDatas.filter { playerData ->
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
        val game = game
        val playerLocation = player.location
        val playerDirection = playerLocation.direction.normalize()

        return game.playerDatas.filter { targetPlayerData ->
            if (!targetPlayerData.playerStatus.isSkillTargeting || targetPlayerData.playerStatus.isDead)
                return@filter false

            if (!includeSelf && targetPlayerData == this)
                return@filter false

            when (targetType) {
                Team -> if (targetPlayerData.team != team) return@filter false
                Enemy -> if (targetPlayerData.team == team) return@filter false
                All -> {}
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
        val nameComponent = MiniMessage.miniMessage().deserialize(name)
        val loreComponents = lore.map { MiniMessage.miniMessage().deserialize(it) }
        return ItemStack(material, 1).apply {
            itemMeta = itemMeta.apply {
                displayName(nameComponent)
                lore(loreComponents)
            }
        }
    }
}