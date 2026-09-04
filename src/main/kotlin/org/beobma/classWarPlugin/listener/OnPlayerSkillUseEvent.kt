package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.ability.AbilityTree

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.gameClass.handler.OtherSkillUseHandler
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.Referee
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class OnPlayerSkillUseEvent : Listener {

    @EventHandler
    fun onPlayerSkillUse(event: PlayerSkillUseEvent) {
        event.context.afterSuccess { dispatchSuccessfulUse(event) }
    }

    private fun dispatchSuccessfulUse(event: PlayerSkillUseEvent) {
        val playerData = event.playerData

        for (bound in AbilityTree.handlers(playerData.gameClasses, OnSkillUseHandler::class.java)) {
            bound.call { it.onSkillUse(event) }
            if (event.isCancelled) return
        }
        // 상태이상
        for (status in playerData.statusAbnormalitys.toList()) {
            if (status !is OnSkillUseHandler) continue
            status.fromSource { status.onSkillUse(event) }
            if (event.isCancelled) return
        }
        playerData.game.playerDatas.asSequence()
            .filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead }
            .forEach { observer ->
                AbilityTree.handlers(observer.gameClasses, OtherSkillUseHandler::class.java)
                    .forEach { bound -> bound.call { it.onOtherPlayerSkillUse(event) } }
            }
        Referee.recordSkillUse(playerData)
    }
}
