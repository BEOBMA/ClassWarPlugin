package org.beobma.classWarPlugin.ability

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

enum class TickPolicy { COMBAT, SESSION, AFTER_DEATH }

/** A synchronous effect task whose timer pauses with combat and whose owner cancels it on removal. */
abstract class AbilityRunnable(
    private val source: AbilityScope? = AbilityExecution.current,
    private val policy: TickPolicy = TickPolicy.COMBAT,
    private val cancelOnDisconnect: Boolean = false,
    private val scheduler: () -> org.bukkit.scheduler.BukkitScheduler = { Bukkit.getScheduler() },
) : Runnable {
    private var task: ManagedTask? = null
    val isCancelled: Boolean get() = task?.isCancelled ?: false
    fun cancel() { task?.cancel() }
    open fun onCancel() {}

    fun runTaskTimer(plugin: Plugin, delay: Long, period: Long): BukkitTask = schedule(plugin, delay, period)
    fun runTaskLater(plugin: Plugin, delay: Long): BukkitTask = schedule(plugin, delay, null)
    fun runTask(plugin: Plugin): BukkitTask = schedule(plugin, 1L, null)

    private fun schedule(plugin: Plugin, delay: Long, period: Long?): BukkitTask {
        check(task == null) { "Effect task has already been scheduled" }
        val scope = checkNotNull(source) { "An ability task must have an explicit owner" }
        val timer = EffectTimer(delay, period)
        val managed = ManagedTask(plugin, scope)
        task = managed
        managed.delegate = scheduler().runTaskTimer(plugin, Runnable {
            if (scope.isClosed || (scope.playerData.entityStatus.isDead && policy == TickPolicy.COMBAT) ||
                (cancelOnDisconnect && (scope.suspended || !scope.playerData.player.isOnline))) {
                managed.cancel()
                return@Runnable
            }
            val suspended = policy != TickPolicy.SESSION && (scope.game.isPaused ||
                (policy == TickPolicy.COMBAT && (!scope.isActive || scope.suspended || !scope.playerData.player.isOnline)))
            if (!timer.advance(suspended)) return@Runnable
            try {
                AbilityExecution.with(scope) { run() }
            } catch (error: Throwable) {
                managed.cancel()
                throw error
            } finally {
                if (timer.complete) managed.cancel()
                scope.resources.prune()
            }
        }, 1L, 1L)
        managed.registration = scope.resources.own { managed.cancel() }
        if (!managed.isCancelled) {
            scope.game.tasks.add(managed)
            if (policy != TickPolicy.AFTER_DEATH) scope.playerData.bukkitTasks.add(managed)
        }
        return managed
    }

    private inner class ManagedTask(private val plugin: Plugin, private val scope: AbilityScope) : BukkitTask {
        var delegate: BukkitTask? = null
        var registration: ResourceScope.Handle? = null
        private var cancelled = false
        override fun getTaskId(): Int = delegate?.taskId ?: -1
        override fun getOwner(): Plugin = plugin
        override fun isSync(): Boolean = true
        override fun isCancelled(): Boolean = cancelled
        override fun cancel() {
            if (cancelled) return
            cancelled = true
            delegate?.cancel()
            registration?.forget()
            scope.playerData.bukkitTasks.remove(this)
            scope.game.tasks.remove(this)
            AbilityExecution.with(scope) { onCancel() }
        }
    }
}
