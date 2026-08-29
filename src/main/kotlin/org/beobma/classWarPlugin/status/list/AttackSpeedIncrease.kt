package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.status.handler.AttackSpeedHandler

open class AttackSpeedIncrease : AttackSpeedHandler() {
    override val name: String
        get() = "<green><bold>공격 속도 증가<gray>"
    override val description: List<String>
        get() = listOf(
            "<gray>공격 속도가 수치에 따라 증가한다.",
            "",
            "<gray>수치 개별 합산 적용",
            "<gray>지속시간 개별 적용",
            "<gray>지속시간 종료 시 개별 소멸"
        )
}
