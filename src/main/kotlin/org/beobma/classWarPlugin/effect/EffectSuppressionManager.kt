package org.beobma.classWarPlugin.effect

import org.bukkit.World
import java.util.UUID

/** 특정 월드의 클래스 파티클과 사운드를 일시적으로 차단한다. */
object EffectSuppressionManager {
    private val suppressedWorlds = mutableMapOf<UUID, Int>()

    fun suppress(world: World): Suppression {
        suppressedWorlds[world.uid] = (suppressedWorlds[world.uid] ?: 0) + 1
        return Suppression(world.uid)
    }

    fun isSuppressed(world: World): Boolean = (suppressedWorlds[world.uid] ?: 0) > 0

    class Suppression internal constructor(private val worldId: UUID) : AutoCloseable {
        private var active = true

        override fun close() {
            if (!active) return
            active = false
            val remaining = (suppressedWorlds[worldId] ?: 1) - 1
            if (remaining > 0) suppressedWorlds[worldId] = remaining else suppressedWorlds.remove(worldId)
        }
    }
}
