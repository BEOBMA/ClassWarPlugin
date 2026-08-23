package org.beobma.classWarPlugin.listener

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
        val playerData = event.playerData

        // 클래스
        for (gameClass in playerData.gameClasses) {
            if (gameClass !is OnSkillUseHandler) continue
            gameClass.onSkillUse(event)
            if (event.isCancelled) return
        }
        // 스킬
        for (skill in playerData.gameClasses.flatMap { it.skills }) {
            if (skill !is OnSkillUseHandler) continue
            skill.onSkillUse(event)
            if (event.isCancelled) return
        }
        // 패시브
        for (passive in playerData.gameClasses.flatMap { it.passives }) {
            if (passive !is OnSkillUseHandler) continue
            passive.onSkillUse(event)
            if (event.isCancelled) return
        }
        // 상태이상
        for (status in playerData.statusAbnormalitys.toList()) {
            if (status !is OnSkillUseHandler) continue
            status.onSkillUse(event)
            if (event.isCancelled) return
        }
        playerData.game.playerDatas.asSequence()
            .filterIsInstance<PlayerData>()
            .filter { it != playerData && !it.entityStatus.isDead }
            .forEach { observer ->
                observer.gameClasses.forEach { observerClass ->
                    (observerClass as? OtherSkillUseHandler)?.onOtherPlayerSkillUse(event)
                    observerClass.passives.filterIsInstance<OtherSkillUseHandler>()
                        .forEach { it.onOtherPlayerSkillUse(event) }
                }
            }
        Referee.recordSkillUse(playerData)
    }
}
