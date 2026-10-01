package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.firearm.FirearmClass
import org.beobma.classWarPlugin.manager.GameManager
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import io.papermc.paper.event.player.PlayerStopUsingItemEvent
import com.destroystokyo.paper.event.player.PlayerJumpEvent

class OnFirearmInputEvent : Listener {
    private fun guns(player: Player): List<FirearmClass> {
        val data = GameManager.findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.firstOrNull { it.uniqueId == player.uniqueId } ?: return emptyList()
        return AbilityTree.nodes(data.gameClasses, true).filterIsInstance<FirearmClass>()
            .filter { it.abilityScope.started && !it.abilityScope.isClosed }
    }
    @EventHandler fun release(event: PlayerStopUsingItemEvent) { guns(event.player).forEach { it.stopTrigger() } }
    @EventHandler fun change(event: PlayerItemHeldEvent) { guns(event.player).forEach { it.stopTrigger() } }
    @EventHandler fun consume(event: PlayerItemConsumeEvent) {
        if (getWeaponClassId(event.item) in setOf("sturmtruppe", "spezialeinheitsmitglied", "schwerekavallerie", "firearmsmaster")) event.isCancelled = true
    }
    @EventHandler(ignoreCancelled = true) fun jump(event: PlayerJumpEvent) {
        if (guns(event.player).any { !it.abilityScope.suspended && it.blocksJump() }) event.isCancelled = true
    }
}
