package org.beobma.classWarPlugin.domain

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.AbilityScope
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.MapTransferBorderManager
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Location
import org.bukkit.World
import java.util.UUID

object DomainManager {
    internal val sessions = mutableListOf<DomainSession>()
    fun expand(scope: AbilityScope, definition: DomainDefinition): Boolean {
        val data = scope.playerData
        if (scope.owner.rank != Rank.SPECIAL || !scope.isActive || scope.suspended ||
            data.entityStatus.isDead || scope.game.isPaused || !data.entityStatus.canSkillUse) return false
        // One domain per world avoids conflicting terrain snapshots and border leases.
        if (sessions.any { it.center.world == data.player.world } || MapTransferBorderManager.isExpanded(data.player.world)) return false
        val target = if (definition.target == DomainTarget.LOOKING_AT_PLAYER) {
            definition.target.resolve(
                data.shotLaserGetEntityData(definition.targetRange, TargetType.Enemy, false) as? PlayerData,
                data, PlayerTagManager.isTraining(data.player)) ?: return false
        } else null
        val center = data.player.location.clone().apply { x = blockX + 0.5; y = blockY.toDouble(); z = blockZ + 0.5 }
        if (!DomainShell.fitsHeight(center.blockY, definition.radius, center.world.minHeight, center.world.maxHeight)) return false
        if (target != null && (target.player.world != center.world || target.player.location.y < center.y - 1 ||
                target.player.location.distance(center) > definition.radius - 2)) return false
        val session = DomainSession(scope, definition, center, target)
        sessions += session
        try { session.start() } catch (error: Throwable) {
            session.close()
            ClassWarPlugin.instance.logger.warning("영역 전개 실패: ${error.message}")
            return false
        }
        return true
    }
    fun isExpanded(world: World) = sessions.any { it.center.world == world }
    fun isDistorted(id: UUID) = sessions.any { it.isIntroducing && id in it.distorted }
    fun timeScale(id: UUID) = if (isDistorted(id)) 0.05 else 1.0
    fun isLocked(id: UUID) = sessions.any { it.isIntroducing && it.caster.uniqueId == id }
    fun blocksMovementSkill(id: UUID): Boolean {
        val player = org.bukkit.Bukkit.getPlayer(id) ?: return false
        val from = player.location
        val aimed = from.clone().add(from.direction.multiply(128.0))
        return sessions.any { id in it.participants || it.crosses(from, aimed) }
    }
    fun blocksCrossing(id: UUID, from: Location, to: Location): Boolean = sessions.any {
        (id in it.participants && !it.contains(to)) ||
            (id !in it.participants && it.contains(to)) ||
            (id !in it.participants && it.crosses(from, to))
    }
    fun clearDomains(ids: Collection<UUID>) = sessions.toList().filter { it.caster.uniqueId in ids }.forEach { it.close() }
    fun shutdown() = sessions.toList().forEach { it.close() }
}
