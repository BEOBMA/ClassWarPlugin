package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusDurationMode

class Frostbite : StatusAbnormality() {
    override val name: String
        get() = Keyword.Frostbite.string
    override val description: List<String>
        get() = listOf(
            Keyword.Frostbite.description!!,
            "",
            "<gray>수치 합산 적용 (최대 수치 10)",
            "<gray>지속시간 갱신",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 10
    override var duration: Int? = 5
    override val durationMode: StatusDurationMode = StatusDurationMode.Refresh

    private var isOn = true
    private var moveSpeedDecrease: MoveSpeedDecrease? = null

    override fun onPowerChanged() {
        val currentMoveSpeedDecrease = moveSpeedDecrease ?: entityData.addStatus(MoveSpeedDecrease(), casterData).also {
            moveSpeedDecrease = it as MoveSpeedDecrease?
        }
        currentMoveSpeedDecrease.updatePower(power * 5)
        currentMoveSpeedDecrease.setContinueWhileIf { isOn }
        super.onPowerChanged()
    }

    override fun onRemoveStatusAbnormality() {
        isOn = false
        super.onRemoveStatusAbnormality()
    }
}
