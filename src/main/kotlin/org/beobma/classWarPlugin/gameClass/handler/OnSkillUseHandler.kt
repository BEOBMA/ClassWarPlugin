package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent

/** 소유 플레이어의 승인 전 스킬 사용 이벤트를 받는 처리기다. */
interface OnSkillUseHandler {
    fun onSkillUse(event: PlayerSkillUseEvent)
}
