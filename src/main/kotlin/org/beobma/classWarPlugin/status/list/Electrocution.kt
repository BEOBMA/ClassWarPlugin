package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusDurationMode

class Electrocution : StatusAbnormality() {
    override val name: String
        get() = Keyword.Electrocution.string
    override val description: List<String>
        get() = listOf(
            Keyword.Electrocution.requireDescription(),
            "",
            "<gray>수치 없음",
            "<gray>지속시간 고정",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 2
    override var duration: Int? = 20
    override val durationMode: StatusDurationMode = StatusDurationMode.Ignore
    override val showMaxPower: Boolean = false
    override val showPower: Boolean = false

    private var isOn = true
    private var moveSpeedDecrease: MoveSpeedDecrease? = null

    override fun onPowerChanged() {
        if (power == 2) {
            val stun = entityData.getOrCreateStatus(casterData) { Stun() }
            stun.applyStatus(
                duration = 2
            )
            this.remove()
            return
        }
        else {
            val currentMoveSpeedDecrease = moveSpeedDecrease ?: entityData.addStatus(MoveSpeedDecrease(), casterData).also {
                moveSpeedDecrease = it as MoveSpeedDecrease?
            }
            currentMoveSpeedDecrease.updatePower(5)
            currentMoveSpeedDecrease.setContinueWhileIf { isOn }
        }
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        isOn = false
        super.onRemoveStatusAbnormality()
    }
}
