package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import java.util.UUID

enum class EndReason { REMOVED, DEATH, GAME_END }

class AbilityScope(val owner: GameClass, val playerData: PlayerData) {
    val instanceId: UUID = UUID.randomUUID()
    val resources = ResourceScope()
    val game get() = playerData.initGame
    val classId get() = owner.classId
    var started = false
    internal var deathNotified = false
    internal var endNotified = false
    var suspended = false
    val isClosed get() = resources.isClosed
    val isActive get() = !isClosed && AbilityTree.nodes(playerData.gameClasses, activeOnly = true).any { it === owner }
}

/** Explicitly bound at handler/skill entry and captured by scheduled effects; never infers a stack frame. */
object AbilityExecution {
    private val source = ThreadLocal<AbilityScope?>()
    val current: AbilityScope? get() = source.get()
    fun <T> with(scope: AbilityScope?, body: () -> T): T {
        val previous = current
        source.set(scope)
        try { return body() } finally { source.set(previous) }
    }
}
