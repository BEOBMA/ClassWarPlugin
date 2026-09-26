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
    private data class BorderLease(val center: Location, val size: Double, val owners: MutableSet<DomainSession> = linkedSetOf())
    private val borders = mutableMapOf<UUID, BorderLease>()
    internal fun acquireBorder(session: DomainSession): Pair<Location, Double> {
        val border = session.center.world.worldBorder
        val lease = borders.getOrPut(session.center.world.uid) { BorderLease(border.center.clone(),border.size) }
        lease.owners += session
        border.changeSize(border.size,0L)
        val required = lease.owners.maxOf { 2*(maxOf(kotlin.math.abs(it.center.x-lease.center.x),
            kotlin.math.abs(it.center.z-lease.center.z))+it.definition.radius+5) }
        border.size = maxOf(lease.size,required).coerceAtMost(border.maxSize)
        return lease.center.clone() to lease.size
    }
    internal fun releaseBorder(session: DomainSession) {
        val lease = borders[session.center.world.uid] ?: return
        lease.owners.remove(session)
        if (lease.owners.isEmpty()) {
            session.center.world.worldBorder.apply { center=lease.center; changeSize(lease.size,0L) }
            borders.remove(session.center.world.uid)
        }
    }
    private fun overlaps(a: DomainSession,b: DomainSession) = a.center.world == b.center.world &&
        DomainOverlap.intersects(a.center.x-b.center.x,a.center.y-b.center.y,a.center.z-b.center.z,a.definition.radius,b.definition.radius)
    internal fun group(seed: DomainSession): List<DomainSession> {
        val active = sessions.filterNot { it.isRestoringTerrain }
        val connected = DomainOverlap.component(seed,active,::overlaps)
        return active.filter { it in connected }
    }
    internal fun coveredByOther(owner: DomainSession,at: Location) = sessions.any {
        it !== owner && !it.isRestoringTerrain && it.center.world == at.world &&
            at.distanceSquared(it.center) < (it.definition.radius+2.0)*(it.definition.radius+2.0)
    }
    fun hasRemainingBoundary(id: UUID, excluding: DomainSession) = sessions.any {
        it !== excluding && !it.isRestoringTerrain && id in it.participants
    }
    internal fun refreshClashes() {
        val active = sessions.toList().filterNot { it.isRestoringTerrain }
        val groups = active.map(::group).distinct()
        val assignments = groups.associateWith { linkedSetOf<UUID>() }
        active.flatMap { it.participants }.distinct().forEach { id ->
            val player = org.bukkit.Bukkit.getPlayer(id) ?: return@forEach
            val eligible = groups.filter { group -> group.any { id in it.participants } }
            val chosen = eligible.firstOrNull { group -> group.any { it.caster.uniqueId == id } }
                ?: eligible.firstOrNull { group -> group.any { it.contains(player.location) } }
                ?: eligible.filter { it.first().center.world == player.world }.minByOrNull { group ->
                    group.minOf { it.center.distanceSquared(player.location) }
                }
            if (chosen != null) assignments.getValue(chosen) += id
        }
        groups.forEach { group ->
            val members = assignments.getValue(group)
            group.forEach { session ->
                session.participants.toList().filter { it !in members && it != session.caster.uniqueId }.forEach(session::removePlayer)
                session.adoptPlayers(members)
                session.updateClash(group.size>1)
            }
        }
        DomainTerrain.repaint()
    }
    fun expand(scope: AbilityScope, definition: DomainDefinition): Boolean {
        val data = scope.playerData
        if (scope.owner.rank != Rank.SPECIAL || !scope.isActive || scope.suspended ||
            data.entityStatus.isDead || scope.game.isPaused || !data.entityStatus.canSkillUse) return false
        if (sessions.any { it.scope === scope && !it.isRestoringTerrain } || MapTransferBorderManager.isExpanded(data.player.world)) return false
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
        refreshClashes()
        try { session.start(); refreshClashes() } catch (error: Throwable) {
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
        return sessions.any { !it.isRestoringTerrain && (id in it.participants || it.crosses(from, aimed)) }
    }
    fun blocksCrossing(id: UUID, from: Location, to: Location): Boolean = sessions.filterNot { it.isRestoringTerrain }.any { seed ->
        val group = group(seed)
        val member = group.any { id in it.participants }
        if (member) group.none { it.contains(to) }
        else group.any { it.contains(to) || it.crosses(from,to) }
    }
    fun clearDomains(ids: Collection<UUID>) = sessions.toList().filter { it.caster.uniqueId in ids }.forEach { it.close() }
    fun shutdown() = sessions.toList().forEach { it.close() }
}
