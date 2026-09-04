package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import java.util.Collections
import java.util.IdentityHashMap

class BoundHandler<T>(val scope: AbilityScope, val handler: T) {
    fun <R> call(body: (T) -> R): R = AbilityExecution.with(scope) { body(handler) }
}

/** The same traversal owns binding, lifecycle and events for direct, stolen and planetary abilities. */
object AbilityTree {
    fun nodes(roots: Collection<GameClass>, activeOnly: Boolean = false): List<GameClass> {
        val seen = Collections.newSetFromMap(IdentityHashMap<GameClass, Boolean>())
        val result = mutableListOf<GameClass>()
        fun visit(node: GameClass) {
            if (!seen.add(node)) return
            result += node
            node.childAbilities.filter { !activeOnly || node.isChildActive(it) }.forEach(::visit)
        }
        roots.forEach(::visit)
        return result
    }

    fun bind(roots: Collection<GameClass>, data: PlayerData) {
        // Children own their components even when a composite exposes them in its item list.
        val components = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        nodes(roots).asReversed().forEach { node ->
            node.inject(data)
            node.skills.forEach { if (components.add(it)) it.bind(data, node) }
            node.passives.forEach { if (components.add(it)) it.bind(data, node) }
        }
    }

    fun <T> handlers(roots: Collection<GameClass>, type: Class<T>, includeDescendants: Boolean = true): List<BoundHandler<T>> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val active = if (includeDescendants) nodes(roots, activeOnly = true) else roots.toList()
        val activeScopes = active.map { it.abilityScope }.toSet()
        return active.flatMap { node ->
            (listOf(node) + node.passives + node.skills).mapNotNull { component ->
                val scope = when (component) {
                    is org.beobma.classWarPlugin.skill.Skill -> component.abilityScope
                    is org.beobma.classWarPlugin.skill.Passive -> component.abilityScope
                    else -> node.abilityScope
                }
                if (scope !in activeScopes || scope.isClosed || !type.isInstance(component) || !seen.add(component)) null
                else BoundHandler(scope, type.cast(component))
            }
        }
    }

    fun start(roots: Collection<GameClass>) {
        nodes(roots, activeOnly = true).forEach { node ->
            if (node.abilityScope.started || node.abilityScope.isClosed) return@forEach
            node.abilityScope.started = true
            AbilityExecution.with(node.abilityScope) {
                node.passives.filter { it.abilityScope === node.abilityScope }
                    .filterIsInstance<GameStatusHandler>().forEach { it.onBattleStart() }
                node.skills.filter { it.abilityScope === node.abilityScope }
                    .filterIsInstance<GameStatusHandler>().forEach { it.onBattleStart() }
                (node as? GameStatusHandler)?.onBattleStart()
            }
        }
    }

    fun end(roots: Collection<GameClass>, reason: EndReason) {
        nodes(roots).asReversed().forEach { node ->
            if (!node.abilityScope.started) return@forEach
            val scope = node.abilityScope
            if (scope.endNotified || (reason == EndReason.DEATH && scope.deathNotified)) return@forEach
            if (reason == EndReason.DEATH) scope.deathNotified = true
            if (reason != EndReason.DEATH || !node.survivesDeath) scope.endNotified = true
            AbilityExecution.with(node.abilityScope) {
                if (reason != EndReason.DEATH || !node.survivesDeath) node.abilityScope.resources.close()
                val components = node.passives.filter { it.abilityScope === node.abilityScope } +
                    node.skills.filter { it.abilityScope === node.abilityScope } + node
                components.forEach {
                    if (reason == EndReason.DEATH && it is PlayerDeathHandler) it.onPlayerDeath()
                    if (scope.endNotified && it is GameEndHandler) it.onGameEnd()
                }
            }
        }
    }

    fun suspend(roots: Collection<GameClass>) = nodes(roots).forEach { node ->
        node.abilityScope.suspended = true
        AbilityExecution.with(node.abilityScope) { node.onSuspend() }
    }

    fun resume(roots: Collection<GameClass>) = nodes(roots).forEach { node ->
        node.abilityScope.suspended = false
        if (!node.abilityScope.isClosed) AbilityExecution.with(node.abilityScope) { node.onResume() }
    }
}
