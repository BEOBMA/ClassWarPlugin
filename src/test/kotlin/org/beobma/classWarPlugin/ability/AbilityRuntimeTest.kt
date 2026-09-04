package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GameConfiguration
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.skill.SkillContext
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.*

private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
        when (method.name) {
            "equals" -> instance === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(instance)
            "toString" -> "Test${T::class.java.simpleName}"
            else -> invoke(method.name, args ?: emptyArray())
        }
    } as T

private class TestItem : ItemStack() {
    override fun getType(): Material = Material.RED_DYE
}

private fun player(id: UUID = UUID.randomUUID(), online: Boolean = true, world: org.bukkit.World? = null): Player = proxy { name, _ ->
    when (name) {
        "getUniqueId" -> id
        "isOnline", "isValid" -> online
        "isDead" -> false
        "getWorld" -> world
        "getScoreboardTags" -> emptySet<String>()
        else -> error("Unexpected player call: $name")
    }
}

private fun participant(): PlayerData {
    val game = Game(mutableListOf(), GameConfiguration(startingItems = emptyList()), tickSource = { 0L })
    return PlayerData(player(), game).also { game.playerDatas += it }
}

private class ProbeSkill : Skill() {
    override val name = "test"
    override val definitionId = "probe/action"
    override val description = emptyList<String>()
    override val cooldown = 1
    var calls = 0
    var accept = true
    var prepared: String? by requestValue { null }
    var requestedTarget: String? = "first"
    var usedTarget: String? = null
    override fun isUseSuccess(): Boolean { prepared = requestedTarget; return true }
    override fun use(): Boolean { calls++; usedTarget = prepared; return accept }
}

private open class ProbeClass(
    override val classId: String = "probe",
    override val childAbilities: List<GameClass> = emptyList(),
    override val survivesDeath: Boolean = false,
) : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler {
    override val name = classId
    override val rank = Rank.A
    override val classItemMaterial = Material.STONE
    val skill = ProbeSkill()
    override val skills: List<Skill> = listOf(skill) + childAbilities.flatMap { it.skills }
    override var passives = emptyList<Passive>()
    var starts = 0
    var ends = 0
    var deaths = 0
    var ticks = 0
    var enabled = true
    override fun isChildActive(child: GameClass) = enabled
    override fun onBattleStart() { starts++; assertSame(abilityScope, AbilityExecution.current) }
    override fun onGameTimePasses() { ticks++; assertSame(abilityScope, AbilityExecution.current) }
    override fun onGameEnd() { ends++ }
    override fun onPlayerDeath() { deaths++ }
    fun currentPlayer(): Player = player
}

class AbilityRuntimeTest {
    @Test fun `nested stolen abilities bind to actual owner and dispatch exactly once`() {
        val leaf = ProbeClass("leaf")
        val root = ProbeClass("root", listOf(leaf))
        val data = participant().also { it.gameClasses += root }
        AbilityTree.bind(data.gameClasses, data)
        AbilityTree.start(data.gameClasses); AbilityTree.start(data.gameClasses)
        assertSame(leaf, leaf.skill.ownerClass)
        assertEquals(1, leaf.starts)
        AbilityTree.handlers(data.gameClasses, GameStatusHandler::class.java).forEach { it.call { h -> h.onGameTimePasses() } }
        assertEquals(1, leaf.ticks)
        val onlyRoot = AbilityTree.handlers(listOf(root), GameStatusHandler::class.java, includeDescendants = false)
        assertEquals(1, onlyRoot.size)
        assertSame(root, onlyRoot.single().handler)
        root.enabled = false
        assertFalse(leaf.abilityScope.isActive)
        AbilityTree.handlers(data.gameClasses, GameStatusHandler::class.java).forEach { it.call { h -> h.onGameTimePasses() } }
        assertEquals(1, leaf.ticks)
        AbilityTree.end(data.gameClasses, EndReason.DEATH)
        AbilityTree.end(data.gameClasses, EndReason.GAME_END)
        assertEquals(1, leaf.deaths)
        assertEquals(1, leaf.ends)
        assertTrue(leaf.abilityScope.isClosed)
    }

    @Test fun `removing one ability preserves another and death survivor closes at game end`() {
        val first = ProbeClass()
        val survivor = ProbeClass("survivor", survivesDeath = true)
        val data = participant().also { it.gameClasses += listOf(first, survivor) }
        AbilityTree.bind(data.gameClasses, data); AbilityTree.start(data.gameClasses)
        var released = 0
        survivor.abilityScope.resources.own { released++ }
        AbilityTree.end(listOf(first), EndReason.REMOVED)
        assertFalse(survivor.abilityScope.isClosed)
        AbilityTree.end(listOf(survivor), EndReason.DEATH)
        AbilityTree.end(listOf(survivor), EndReason.DEATH)
        assertEquals(1, survivor.deaths)
        assertEquals(0, released)
        AbilityTree.end(listOf(survivor), EndReason.GAME_END)
        assertEquals(1, released)
        assertEquals(1, survivor.ends)
    }

    @Test fun `reconnect rebinds nested current player without resetting battle resources`() {
        val leaf = ProbeClass("leaf")
        val root = ProbeClass("root", listOf(leaf))
        val data = participant().also { it.gameClasses += root }
        AbilityTree.bind(data.gameClasses, data); AbilityTree.start(data.gameClasses)
        val originalId = leaf.skill.id
        AbilityTree.suspend(data.gameClasses)
        data.player = player(data.uniqueId)
        AbilityTree.bind(data.gameClasses, data); AbilityTree.resume(data.gameClasses)
        assertSame(data.player, leaf.currentPlayer())
        assertFalse(leaf.abilityScope.suspended)
        assertEquals(1, leaf.starts)
        assertEquals(originalId, leaf.skill.id)
    }

    @Test fun `failed and cancelled skill requests discard prepared targets and do not execute`() {
        val ability = ProbeClass()
        val data = participant().also { it.gameClasses += ability }
        AbilityTree.bind(data.gameClasses, data)
        val skill = ability.skill
        fun context() = SkillContext(data, skill, TestItem(), 20)
        val rejected = context()
        assertFalse(skill.request(rejected) { false })
        assertEquals(0, skill.calls)
        assertTrue(rejected.preparedValues.isEmpty())
        skill.requestedTarget = null
        skill.accept = false
        assertFalse(skill.request(context()) { true })
        assertNull(skill.usedTarget)
        skill.accept = true
        skill.requestedTarget = "next"
        assertTrue(skill.request(context()) { true })
        assertEquals("next", skill.usedTarget)
        assertFailsWith<IllegalStateException> { skill.prepared }
        assertNull(AbilityExecution.current)
    }

    @Test fun `approval cannot execute a replaced ability and nested source context is restored`() {
        val first = ProbeClass()
        val second = ProbeClass("second")
        val data = participant().also { it.gameClasses += listOf(first, second) }
        AbilityTree.bind(data.gameClasses, data); AbilityTree.start(data.gameClasses)
        val context = SkillContext(data, first.skill, TestItem(), 20)
        AbilityExecution.with(second.abilityScope) {
            assertFalse(first.skill.request(context) { AbilityTree.end(listOf(first), EndReason.REMOVED); true })
            assertSame(second.abilityScope, AbilityExecution.current)
        }
        assertEquals(0, first.skill.calls)
        assertNotEquals(first.skill.id, second.skill.id)
        assertTrue(second.skill.matchesId(second.skill.javaClass.name))
        assertTrue(second.skill.matchesId(second.skill.definitionId))
        assertFalse(second.skill.matchesId(first.skill.id))
    }

    @Test fun `scheduled effects pause resume with fresh player and cancel with cleanup`() {
        val ability = ProbeClass()
        val data = participant().also { it.gameClasses += ability }
        AbilityTree.bind(data.gameClasses, data); AbilityTree.start(data.gameClasses)
        var tick: Runnable? = null
        var nativeCancelled = false
        val plugin = proxy<Plugin> { name, _ -> if (name == "getName") "test" else null }
        val nativeTask = proxy<BukkitTask> { name, _ ->
            when (name) { "cancel" -> { nativeCancelled = true; null }; "isCancelled" -> nativeCancelled; "getTaskId" -> 1; else -> null }
        }
        val scheduler = proxy<BukkitScheduler> { name, args ->
            check(name == "runTaskTimer"); tick = args[1] as Runnable; nativeTask
        }
        var runs = 0
        var cleanup = 0
        val task = object : AbilityRunnable(ability.abilityScope, scheduler = { scheduler }) {
            override fun run() { assertSame(ability.abilityScope, AbilityExecution.current); runs++ }
            override fun onCancel() { cleanup++ }
        }.runTaskTimer(plugin, 2, 2)
        val runTick = checkNotNull(tick)
        runTick.run()
        data.game.isPaused = true
        repeat(20) { runTick.run() }
        data.game.isPaused = false
        AbilityTree.suspend(data.gameClasses)
        runTick.run()
        data.player = player(data.uniqueId)
        AbilityTree.resume(data.gameClasses)
        runTick.run()
        assertEquals(1, runs)
        AbilityTree.end(data.gameClasses, EndReason.REMOVED)
        task.cancel()
        assertEquals(1, cleanup)
        assertTrue(nativeCancelled)
        assertTrue(data.bukkitTasks.isEmpty())
        assertTrue(data.game.tasks.isEmpty())
    }

    @Test fun `target queries ignore other worlds dead offline and untargetable entities without registration`() {
        val world = proxy<org.bukkit.World> { name, _ -> error("A query must not register or scan the world: $name") }
        val otherWorld = proxy<org.bukkit.World> { name, _ -> error("Unexpected other-world call: $name") }
        val game = Game(mutableListOf(), GameConfiguration(startingItems = emptyList()), tickSource = { 0 })
        fun add(online: Boolean = true, targetWorld: org.bukkit.World = world) =
            PlayerData(player(online = online, world = targetWorld), game).also { game.playerDatas += it }
        val source = add()
        val enemy = add()
        add().entityStatus.isDead = true
        add().entityStatus.isSkillTargeting = false
        add(online = false)
        add(targetWorld = otherWorld)
        game.playerDatas += enemy
        val before = game.playerDatas.toList()
        assertEquals(listOf(enemy), Targeting.select(source, org.beobma.classWarPlugin.util.TargetType.Enemy))
        assertEquals(listOf(source), Targeting.select(source, org.beobma.classWarPlugin.util.TargetType.Self, includeSelf = true))
        assertEquals(before, game.playerDatas)
    }

    @Test fun `a shared self status survives removal of one of its owners`() {
        val first = ProbeClass("first")
        val second = ProbeClass("second")
        val data = participant().also { it.gameClasses += listOf(first, second) }
        AbilityTree.bind(data.gameClasses, data); AbilityTree.start(data.gameClasses)
        var removed = 0
        val status = object : org.beobma.classWarPlugin.status.StatusAbnormality() {
            override val name = "shared"
            override val description = emptyList<String>()
            override val canRemove = false
            override fun onRemoveStatusAbnormality() { removed++ }
        }
        status.inject(data, data)
        data.statusAbnormalitys += status
        status.retain(first.abilityScope); status.retain(second.abilityScope)
        status.retain(second.abilityScope)
        AbilityTree.end(listOf(first), EndReason.REMOVED)
        assertSame(status, data.statusAbnormalitys.single())
        assertEquals(0, removed)
        AbilityTree.end(listOf(second), EndReason.REMOVED)
        assertTrue(data.statusAbnormalitys.isEmpty())
        assertEquals(1, removed)
    }

    @Test fun `cooldowns freeze with the game and preserve individual skill pauses`() {
        var tick = 0L
        val game = Game(mutableListOf(), GameConfiguration(startingItems = emptyList()), tickSource = { tick })
        val id = UUID.randomUUID()
        val player = proxy<Player> { name, _ ->
            when (name) { "getUniqueId" -> id; "getScoreboardTags" -> emptySet<String>(); "setCooldown" -> null; else -> error(name) }
        }
        val ability = ProbeClass()
        val data = PlayerData(player, game).also { game.playerDatas += it; it.gameClasses += ability }
        AbilityTree.bind(data.gameClasses, data)
        org.beobma.classWarPlugin.info.Info.game = game
        val cooldowns = org.beobma.classWarPlugin.manager.CooldownManager
        try {
            cooldowns.setCooldown(player, ability.skill, TestItem(), 20)
            tick = 5
            assertEquals(15, cooldowns.remainingTicks(player, ability.skill))
            game.isPaused = true
            tick += 100
            assertEquals(15, cooldowns.remainingTicks(player, ability.skill))
            game.isPaused = false
            cooldowns.pauseCooldown(player, ability.skill)
            tick += 10
            cooldowns.resumeCooldown(player, ability.skill)
            assertEquals(15, cooldowns.remainingTicks(player, ability.skill))
            tick += 15
            assertEquals(0, cooldowns.remainingTicks(player, ability.skill))
        } finally {
            cooldowns.clear(listOf(id))
            org.beobma.classWarPlugin.info.Info.game = null
        }
    }
}
