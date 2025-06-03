package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.status.MoveSpeedHandler

class MoveSpeedDecrease : MoveSpeedHandler() {
    override val name: String
        get() = "<red><bold>이동 속도 감소<gray>"
    override val description: List<String>
        get() = listOf(
            "<gray>이동 속도가 수치에 따라 감소한다.",
            "",
            "<dark_gray>최대치 없음."
        )
    override var maxPower: Int? = null
    override var duration: Int? = null
    override val canRemove: Boolean = true
}