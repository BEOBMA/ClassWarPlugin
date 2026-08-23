package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class OnPlayerSkillUseEvent : Listener {

    @EventHandler
    fun onPlayerSkillUse(event: PlayerSkillUseEvent) {
        val playerData = event.playerData
        val gameClass = playerData.gameClass ?: return

        // 클래스
        if (gameClass is OnSkillUseHandler) {
            gameClass.onSkillUse(event)
            if (event.isCancelled) return
        }
        // 스킬
        for (skill in gameClass.skills) {
            if (skill !is OnSkillUseHandler) continue
            skill.onSkillUse(event)
            if (event.isCancelled) return
        }
        // 패시브
        for (passive in gameClass.passives) {
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
    }
}
