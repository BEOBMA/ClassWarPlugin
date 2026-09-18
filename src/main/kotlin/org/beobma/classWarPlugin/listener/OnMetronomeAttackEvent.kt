package org.beobma.classWarPlugin.listener

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.ability.AbilityExecution
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.CooperativeAction
import org.beobma.classWarPlugin.gameClass.list.Metronome
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.status.list.Disarm
import org.bukkit.entity.Player
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class OnMetronomeAttackEvent : Listener {
    private fun classes(player: Player): List<Metronome> {
        val data = findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.firstOrNull { it.uniqueId == player.uniqueId } ?: return emptyList()
        if (!data.canDispatchClassHandlers() || data.game.isPaused || !data.entityStatus.canAttack ||
            data.hasStatus<Disarm>() || !data.game.canPerform(data.uniqueId, CooperativeAction.BASIC_ATTACK)) return emptyList()
        return AbilityTree.nodes(data.gameClasses, activeOnly = true).filterIsInstance<Metronome>()
            .filter { it.abilityScope.started && !it.abilityScope.isClosed && !it.abilityScope.suspended }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAttack(event: PrePlayerAttackEntityEvent) {
        val target = event.attacked as? LivingEntity ?: return
        val abilities = classes(event.player)
        if (abilities.isEmpty()) return
        var allowed = true
        abilities.forEach { ability ->
            if (!AbilityExecution.with(ability.abilityScope) { ability.attempt(target.uniqueId) }) allowed = false
        }
        if (!allowed) event.isCancelled = true
        else target.noDamageTicks = 0 // permit 4/8 subdivisions against the same player or training mob
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onAirSwing(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != Action.LEFT_CLICK_AIR) return
        classes(event.player).forEach { ability -> AbilityExecution.with(ability.abilityScope) { ability.attempt(null) } }
    }
}
