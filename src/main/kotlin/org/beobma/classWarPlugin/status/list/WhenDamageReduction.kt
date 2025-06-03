package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.status.WhenDamageHandler

class WhenDamageReduction : WhenDamageHandler() {
    override val name: String
        get() = "<green><bold>받는 피해 감소<gray>"
    override val description: List<String>
        get() = listOf(
            "<gray>받는 피해가 수치에 따라 감소한다.",
            "",
            "<dark_gray>최대치 없음."
        )
}