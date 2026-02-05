package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.GameManager.trainingInstance
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.list.Shield
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.math.roundToInt

class OnEntityDamageEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity.isMannequin()) {
            event.isCancelled = true
        }
        val player = event.entity as? Player ?: return
        if (!PlayerTagManager.hasTag(player, "isTraining")) {
            return
        }
        val game = trainingInstance.find { game -> game.playerDatas.any { playerData -> playerData.entity == player} } ?: return
        val playerData = game.playerDatas.find { playerData -> playerData.entity == player } ?: return
        val shield = playerData.getStatus<Shield>()
        if (shield != null) {
            val damage = event.damage.roundToInt()
            val remainDamage = (damage - shield.power).coerceAtLeast(0)
            val remainShield = (shield.power - damage).coerceAtLeast(0)

            event.damage = remainDamage.toDouble()

            if (remainShield == 0) {
                shield.remove()
            } else {
                shield.updatePower(remainShield)
            }
        }

        val finalDamage = event.damage
        if (finalDamage > 0.0) {
            val formattedDamage = String.format("%.2f", finalDamage)
            player.sendMiniMessage("<red>받은 피해 정보 - <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
        }
        event.isCancelled = true
    }
}
