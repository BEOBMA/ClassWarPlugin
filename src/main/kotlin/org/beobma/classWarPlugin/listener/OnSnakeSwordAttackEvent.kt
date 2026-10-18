package org.beobma.classWarPlugin.listener

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.beobma.classWarPlugin.ability.AbilityExecution
import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.SnakeSword
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** Entity clicks do not emit LEFT_CLICK_AIR, so they need the same replacement swing explicitly. */
class OnSnakeSwordAttackEvent : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun attack(event: PrePlayerAttackEntityEvent) {
        if (getWeaponClassId(event.player.inventory.itemInMainHand) != "snake-sword") return
        val data = findGameForPlayer(event.player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.firstOrNull { it.uniqueId == event.player.uniqueId } ?: return
        if (!data.canDispatchClassHandlers()) return
        val abilities = AbilityTree.nodes(data.gameClasses, activeOnly = true).filterIsInstance<SnakeSword>()
        if (abilities.isEmpty()) return
        event.isCancelled = true
        if (org.beobma.classWarPlugin.domain.DomainManager.isLocked(event.player.uniqueId)) return
        abilities.forEach { ability -> AbilityExecution.with(ability.abilityScope) { ability.swing() } }
    }
}
