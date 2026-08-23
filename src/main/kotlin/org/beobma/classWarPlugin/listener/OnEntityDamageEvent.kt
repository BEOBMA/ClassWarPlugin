package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.manager.GameManager.trainingInstance
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.game.GamePhase
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.DamageIndicatorManager
import org.beobma.classWarPlugin.manager.CombatManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.status.list.Invincibility
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.GameManager.canDispatchClassHandlers
import org.beobma.classWarPlugin.gameClass.list.Vampire
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.math.roundToInt

class OnEntityDamageEvent : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (Vampire.handleBatDamage(event)) return
        if (event.entity.isMannequin()) {
            event.isCancelled = true
        }
        val player = event.entity as? Player ?: return
        val handlerData = findGameForPlayer(player)?.playerDatas?.filterIsInstance<PlayerData>()
            ?.find { it.uniqueId == player.uniqueId }
        if (handlerData?.hasStatus<Invincibility>() == true) {
            event.isCancelled = true
            return
        }
        val activeGame = game
        if (activeGame != null) {
            val playerData = activeGame.playerDatas.filterIsInstance<PlayerData>()
                .find { it.player == player }
            if (playerData != null && (activeGame.phase != GamePhase.RUNNING || !playerData.entityStatus.isAttackable)) {
                event.isCancelled = true
                return
            }
        }
        if (handlerData != null && !handlerData.canDispatchClassHandlers()) {
            event.isCancelled = true
            return
        }
        handlerData?.gameClasses?.filterIsInstance<EnvironmentalDamageHandler>()?.forEach { handler ->
            handler.onEnvironmentalDamage(event)
            if (event.isCancelled) return
        }
        if (event.isCancelled) return
        if (!PlayerTagManager.hasTag(player, "isTraining")) {
            if (handlerData != null && event.finalDamage > 0.0) {
                CombatManager.recordDamageTaken(handlerData)
            }
            return
        }
        val game = trainingInstance.find { game -> game.playerDatas.any { playerData -> playerData.entity == player} } ?: return
        val playerData = game.playerDatas.filterIsInstance<PlayerData>()
            .find { playerData -> playerData.entity == player } ?: return
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

        val finalDamage = event.finalDamage
        if (finalDamage > 0.0) {
            CombatManager.recordDamageTaken(playerData)
            player.playHurtAnimation(0.0f)
            DamageIndicatorManager.show(player, finalDamage, game.settings.damageIndicatorsEnabled)
            val formattedDamage = String.format("%.2f", finalDamage)
            player.sendMiniMessage("<red>받은 피해 정보 - <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
        }
        event.isCancelled = true
    }
}
