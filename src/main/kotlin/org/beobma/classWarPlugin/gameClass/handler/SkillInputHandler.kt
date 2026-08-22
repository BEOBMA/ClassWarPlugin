package org.beobma.classWarPlugin.gameClass.handler

import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.event.player.PlayerInteractEvent

/** 우클릭 외의 입력으로 스킬 아이템을 사용하는 클래스가 구현하는 훅. */
interface SkillInputHandler {
    /** 이 입력으로 스킬을 실행할 경우 true를 반환하고, 실행 전 필요한 상태를 준비한다. */
    fun prepareSkillInput(event: PlayerInteractEvent, skill: Skill): Boolean
}
