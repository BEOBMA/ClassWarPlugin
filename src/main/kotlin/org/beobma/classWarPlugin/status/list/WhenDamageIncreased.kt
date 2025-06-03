package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.status.WhenDamageHandler

class WhenDamageIncreased : WhenDamageHandler() {
    override val name: String
        get() = "<red><bold>받는 피해 증가<gray>"
    override val description: List<String>
        get() = listOf(
            "<gray>받는 피해가 수치에 따라 증가한다.",
            "",
            "<dark_gray>최대치 없음."
        )
}