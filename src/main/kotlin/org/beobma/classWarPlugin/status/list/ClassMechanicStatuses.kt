package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.Location

class CheckpointStatus(
    var savedLocation: Location,
    var savedHealth: Double,
) : StatusAbnormality() {
    override val name = Keyword.Checkpoint.string
    override val description = listOf(Keyword.Checkpoint.description ?: "")
    override val canRemove = true
    override val isClassMechanic = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = 15
}

class GunBulletStatus : StatusAbnormality() {
    override val name = Keyword.Bullet.string
    override val description = listOf(Keyword.Bullet.description ?: "")
    override val canRemove = false
    override val isClassMechanic = true
    override var maxPower: Int? = 4
    override var duration: Int? = null
}

class GamblerCardStatus : StatusAbnormality() {
    private var cards: List<Int> = emptyList()

    override val name = Keyword.Card.string
    override val description = listOf(Keyword.Card.description ?: "")
    override val canRemove = false
    override val isClassMechanic = true
    override var maxPower: Int? = null
    override val showMaxPower = false
    override var duration: Int? = null

    fun updateCards(newCards: List<Int>) {
        cards = newCards.toList()
        updatePower(cards.sum())
    }

    override fun actionBarText(): String {
        val hand = if (cards.isEmpty()) "-" else cards.joinToString(" <dark_gray>·</dark_gray> ")
        return "$name: <gold>[$hand]</gold> <gray>(합계 <yellow>$power</yellow>)</gray>"
    }
}

class TimePhaseStatus : StatusAbnormality() {
    var phaseLabel: String = "여명"
        private set

    override val name = Keyword.TimePhase.string
    override val description = listOf(Keyword.TimePhase.description ?: "")
    override val canRemove = false
    override val isClassMechanic = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = 12

    fun updatePhase(label: String, seconds: Int) {
        phaseLabel = label
        updatePower(1)
        updateDuration(seconds)
    }

    override fun actionBarText(): String =
        "$name: <gold>$phaseLabel</gold> <dark_gray>|</dark_gray><yellow>${duration ?: 0}s</yellow>"
}

class SniperAmmoStatus : StatusAbnormality() {
    private var reloading = false
    private var reloadTicksRemaining = 0
    private var reloadTotalTicks = 40
    override val name = Keyword.Bullet.string
    override val description = listOf(Keyword.Bullet.description ?: "")
    override val canRemove = false
    override val isClassMechanic = true
    override var maxPower: Int? = 1
    override var duration: Int? = null

    fun setLoaded(loaded: Boolean) {
        reloading = false
        reloadTicksRemaining = 0
        updateDuration(null)
        updatePower(if (loaded) 1 else 0)
    }

    fun setReloading(seconds: Int) {
        reloading = true
        reloadTotalTicks = seconds * 20
        reloadTicksRemaining = reloadTotalTicks
        updateDuration(null)
        updatePower(1)
    }

    fun updateReloadTicks(ticks: Int) {
        if (!reloading) return
        reloadTicksRemaining = ticks.coerceAtLeast(0)
        updatePower(1)
    }

    override fun actionBarText(): String {
        if (reloading && reloadTicksRemaining > 0) {
            val progress = 1.0 - reloadTicksRemaining.toDouble() / reloadTotalTicks.coerceAtLeast(1)
            val filled = (progress * 10.0).toInt().coerceIn(0, 10)
            val gauge = "<green>${"▰".repeat(filled)}</green><dark_gray>${"▱".repeat(10 - filled)}</dark_gray>"
            val seconds = String.format(java.util.Locale.US, "%.1f", reloadTicksRemaining / 20.0)
            return "$name: <yellow>재장전 중</yellow> $gauge <yellow>${seconds}s</yellow>"
        }
        if (power > 0) return "$name: <green><bold>장전 완료</bold></green>"
        return "$name: <red><bold>비어 있음</bold></red>"
    }
}
