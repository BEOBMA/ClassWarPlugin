package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.status.WhenDamageHandler

class WhenDamageReduction : WhenDamageHandler() {
    override val name: String
        get() = "<green><bold>받는 피해 감소<gray>"
    override val description: List<String>
        get() = listOf(
            "<gray>받는 피해가 수치에 따라 감소한다.",
            "",
            "<gray>수치 개별 합산 적용",
            "<gray>지속시간 개별 적용",
            "<gray>지속시간 종료 시 개별 소멸"
        )
}