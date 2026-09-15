package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GameConfiguration
import org.beobma.classWarPlugin.game.MatchMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.*

class GrowthEventRuntimeTest {
    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "EventProbe"
                else -> answer(method.name)
            }
        } as T

    private fun field(runtime: GrowthModeRuntime, name: String) =
        GrowthModeRuntime::class.java.getDeclaredField(name).apply { isAccessible = true }

    private inner class Fixture {
        val game = Game(mutableListOf(), GameConfiguration(startingItems = emptyList()), mode = MatchMode.GROWTH, tickSource = { 0L })
        val world: World
        val runtime: GrowthModeRuntime
        val events: GrowthEventTracker
        val region = GrowthRegion(0, listOf(RegionRect(0, 0, 1, 1)), "숲", "forest", intArrayOf(0), emptySet(), null)
        val loc: Location
        init {
            lateinit var fakeWorld: World
            val border: WorldBorder = proxy { when (it) {
                "getCenter" -> Location(fakeWorld, 0.0, 0.0, 0.0)
                "getSize" -> 320.0
                "getDamageAmount", "getDamageBuffer" -> 0.0
                "getWarningDistance", "getWarningTime" -> 0
                else -> error(it)
            } }
            fakeWorld = proxy { if (it == "getWorldBorder") border else error(it) }
            world = fakeWorld
            loc = Location(world, 0.5, 64.0, 0.5)
            runtime = GrowthModeRuntime(game, world)
            game.growth = runtime
            field(runtime, "layout").set(runtime, RegionLayout(
                SurfaceSnapshot(0, 0, 1, intArrayOf(64), arrayOf("forest")), listOf(region), intArrayOf(0), 0))
            events = field(runtime, "events").get(runtime) as GrowthEventTracker
        }

        fun plan(monster: Boolean = false): GrowthEventDefinition = GrowthEventDefinition("test", 2, false,
            emptySet(), "world-tree", 90, if (monster) EntityType.SKELETON else null).also {
            events.plan(it, 0, loc)
        }
    }

    @Test fun `real item pickup removes marker and grants equipment exactly once`() {
        val f = Fixture()
        val event = f.plan()
        assertFalse(f.runtime.eventMarkers().single().active)
        assertContains(f.runtime.eventMarkers().single().name, "2일차 낮 예정")
        val playerId = UUID.randomUUID()
        val player: Player = proxy { when (it) {
            "getUniqueId" -> playerId; "isOnline" -> true; "isDead" -> false; "sendMessage" -> null
            else -> error(it)
        } }
        val data = PlayerData(player, f.game)
        f.game.playerDatas += data
        f.runtime.players[playerId] = GrowthPlayerState()
        val id = UUID.randomUUID()
        var removed = false
        val item: Item = proxy { when (it) {
            "getUniqueId" -> id; "getLocation" -> f.loc; "isValid" -> !removed; "isDead" -> removed
            "remove" -> { removed = true; null }
            else -> error(it)
        } }
        val dropClass = GrowthModeRuntime::class.java.declaredClasses.single { it.simpleName == "EventDrop" }
        val constructor = dropClass.declaredConstructors.single().apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val drops = field(f.runtime, "drops").get(f.runtime) as MutableMap<UUID, Any>
        drops[id] = constructor.newInstance(item, event, 0, 90)
        f.events.takeDue(2); f.events.activate(event.id, id)
        assertTrue(f.runtime.eventMarkers().single().active)
        assertTrue(f.runtime.claim(item, player))
        assertTrue(removed)
        assertTrue(f.runtime.eventMarkers().isEmpty())
        assertEquals(setOf("world-tree"), f.runtime.players.getValue(playerId).inventory)
        assertFalse(f.runtime.claim(item, player))
    }

    @Test fun `real monster death removes marker and runtime entity record`() {
        val f = Fixture()
        val event = f.plan(true)
        assertTrue(f.runtime.eventMarkers().single().monster)
        val id = UUID.randomUUID()
        var removed = false
        val mob: LivingEntity = proxy { when (it) {
            "getUniqueId" -> id; "getLocation" -> f.loc.clone().add(0.2, 0.0, 0.0)
            "isValid" -> !removed; "isDead" -> removed
            "remove" -> { removed = true; null }
            else -> error(it)
        } }
        val data = MobEntityData(mob, f.game)
        f.game.playerDatas += data
        f.runtime.mobs[id] = GrowthModeRuntime.MobRecord(data, 0, f.loc, 1, event, 90)
        f.events.takeDue(2); f.events.activate(event.id, id)
        assertEquals(0.7, f.runtime.eventMarkers().single().location.x)
        f.runtime.mobDeath(id, null)
        assertTrue(removed)
        assertTrue(f.runtime.eventMarkers().isEmpty())
        assertTrue(f.runtime.mobs.isEmpty())
        assertTrue(f.game.playerDatas.isEmpty())
        f.runtime.mobDeath(id, null)
        assertTrue(f.events.takeDue(2).isEmpty())
    }

    @Test fun `expired invalid and forbidden objectives are hidden before the cleanup tick`() {
        val f = Fixture()
        val event = f.plan(true)
        val id = UUID.randomUUID()
        var valid = true
        val mob: LivingEntity = proxy { when (it) {
            "getUniqueId" -> id; "getLocation" -> f.loc; "isValid" -> valid; "isDead" -> false
            else -> error(it)
        } }
        f.runtime.mobs[id] = GrowthModeRuntime.MobRecord(MobEntityData(mob, f.game), 0, f.loc, 1, event, 90)
        f.events.takeDue(2); f.events.activate(event.id, id)
        valid = false
        assertTrue(f.runtime.eventMarkers().isEmpty())
        valid = true
        f.region.state = RegionState.FORBIDDEN
        assertTrue(f.runtime.eventMarkers().isEmpty())
        f.region.state = RegionState.SAFE
        field(f.runtime, "seconds").setInt(f.runtime, 90)
        assertTrue(f.runtime.eventMarkers().isEmpty())
    }
}
