package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.event.PlayerSkillUseEvent
import org.beobma.classWarPlugin.gameClass.handler.OnSkillUseHandler
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.StatusAbnormality

class Silence : StatusAbnormality(), OnSkillUseHandler {
    override val name: String
        get() = Keyword.Silence.string
    override val description: List<String>
        get() = listOf(
            Keyword.Silence.description!!,
            "",
            "<gray>수치 없음",
            "<gray>지속시간 연장 적용",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var power: Int = 1
    override val showMaxPower: Boolean = false
    override val showPower: Boolean = false
    override var duration: Int? = null

    override fun onSkillUse(event: PlayerSkillUseEvent) {
        event.playerData.player.sendMiniMessage("<red><bold>[!] 침묵 상태에서는 스킬을 사용할 수 없습니다.")
        event.isCancelled = true
    }
}