package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent

/** 같은 게임의 다른 플레이어가 스킬을 사용할 때 호출되는 관찰 훅. */
interface OtherSkillUseHandler {
    fun onOtherPlayerSkillUse(event: PlayerSkillUseEvent)
}
