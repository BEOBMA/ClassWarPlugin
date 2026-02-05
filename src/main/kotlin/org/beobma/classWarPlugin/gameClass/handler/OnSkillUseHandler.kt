package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent

interface OnSkillUseHandler {
    fun onSkillUse(event: PlayerSkillUseEvent)
}