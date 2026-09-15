package org.beobma.classWarPlugin.growth

import org.beobma.classWarPlugin.ability.AbilityExecution
import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.description.GrowthDescription
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GameConfiguration
import org.beobma.classWarPlugin.game.MatchMode
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.manager.ClassBalanceManager
import org.beobma.classWarPlugin.manager.DamageManager
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.*

class GrowthDamageIntegrationTest {
    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "GrowthProbe"
                else -> answer(method.name)
            }
        } as T

    private class Probe(override val classId: String) : GameClass() {
        override val name = classId
        override val rank = Rank.A
        override val classItemMaterial = Material.STONE
        override val skills = emptyList<Skill>()
        override var passives = emptyList<Passive>()
    }

    private fun participant(growth: Boolean = true): Pair<PlayerData, List<Probe>> {
        val game = Game(mutableListOf(), GameConfiguration(startingItems = emptyList()),
            mode = if (growth) MatchMode.GROWTH else MatchMode.CLASSIC, tickSource = { 0L })
        val id = UUID.randomUUID()
        val player: Player = proxy { when (it) { "getUniqueId" -> id; "getName" -> "probe"; else -> error(it) } }
        val data = PlayerData(player, game)
        val classes = listOf(Probe("berserker"), Probe("ice-wizard"))
        data.gameClasses.addAll(classes); game.playerDatas += data
        AbilityTree.bind(data.gameClasses, data)
        if (growth) {
            lateinit var world: World
            val border: WorldBorder = proxy { when (it) {
                "getCenter" -> Location(world, 0.0, 0.0, 0.0)
                "getSize" -> 320.0
                "getDamageAmount", "getDamageBuffer" -> 0.0
                "getWarningDistance", "getWarningTime" -> 0
                else -> error(it)
            } }
            world = proxy { if (it == "getWorldBorder") border else error(it) }
            game.growth = GrowthModeRuntime(game, world)
            val state = game.growth!!.players.getValue(id)
            state.gain(100000, game.settings.growth)
            assertTrue(state.allocate(GrowthStat.STRENGTH, 30))
            assertTrue(state.allocate(GrowthStat.INTELLIGENCE, 30))
        }
        return data to classes
    }

    @Test fun `real damage pipeline separates basic and ranged from skills and status effects`() {
        val (data, classes) = participant()
        AbilityExecution.with(classes[1].abilityScope) {
            val basic = ClassBalanceManager.scaleDamage(data, DamagePath.BASIC_ATTACK, 10.0, "ice-wizard")
            val ranged = ClassBalanceManager.scaleDamage(data, DamagePath.RANGED_ATTACK, 10.0, "ice-wizard")
            val skill = ClassBalanceManager.scaleDamage(data, DamagePath.SKILL, 10.0)
            val status = ClassBalanceManager.scaleDamage(data, DamagePath.STATUS_EFFECT, 10.0)
            assertEquals(basic, ranged)
            assertEquals(skill, status)
            assertTrue(skill > basic)
            assertEquals(10.0 * GrowthProfile.forClass("ice-wizard").multiplier(GrowthAxis.SKILL_DAMAGE,
                data.game.growth!!.players.getValue(data.uniqueId)::stat), skill)
        }
    }

    @Test fun `flat passive bonuses grow once under their own class not the first dual class`() {
        val (data, classes) = participant()
        AbilityExecution.with(classes[1].abilityScope) {
            val context = DamageContext(data, data, DamagePath.BASIC_ATTACK, DamageType.Normal, 10.0, weaponClassId = "ice-wizard")
            val original = context.damage
            context.addBaseDamage(2.0)
            val p = GrowthProfile.forClass("ice-wizard")
            val expected = 2.0 * p.multiplier(GrowthAxis.BASIC_DAMAGE, data.game.growth!!.players.getValue(data.uniqueId)::stat)
            assertEquals(expected, context.damage - original, 0.000001)
        }
    }

    @Test fun `numeric previews match real damage and status scaling under the owning dual class`() {
        val (data, classes) = participant()
        AbilityExecution.with(classes[1].abilityScope) {
            val preview = assertNotNull(GrowthDescription.forData(data, "ice-wizard"))
            for ((operation, path) in listOf("damage" to DamagePath.SKILL,
                "status-damage" to DamagePath.STATUS_EFFECT, "ranged" to DamagePath.RANGED_ATTACK)) {
                val hit = DamageContext(data, data, path, DamageType.Normal, 10.0, weaponClassId = "ice-wizard")
                val normalization = if (path.isBasicAttack) DamageManager.BASIC_ATTACK_DAMAGE_MULTIPLIER else 1.0
                assertEquals(hit.originalDamage * normalization,
                    GrowthDescription.evaluate(operation, 10.0, preview).first, 0.000001, operation)
            }
            val hit = DamageContext(data, data, DamagePath.BASIC_ATTACK, DamageType.Normal, 10.0,
                weaponClassId = "ice-wizard")
            val before = hit.damage
            hit.addBaseDamage(2.0)
            assertEquals((hit.damage - before) * DamageManager.BASIC_ATTACK_DAMAGE_MULTIPLIER,
                GrowthDescription.evaluate("attack-bonus", 2.0, preview).first, 0.000001)
            assertEquals(ClassBalanceManager.scaleHealing(data, 5.0),
                GrowthDescription.evaluate("healing", 5.0, preview).first, 0.000001)
            assertEquals(ClassBalanceManager.scaleStatusDuration(data, 8).toDouble(),
                GrowthDescription.evaluate("duration", 8.0, preview).first)
            assertEquals(ClassBalanceManager.scaleStatusPower(data, 5, GrowthAxis.SPEED).toDouble(),
                GrowthDescription.evaluate("speed", 5.0, preview).first)
        }
    }

    @Test fun `classic damage and bespoke class counts remain unchanged`() {
        val (data, classes) = participant(false)
        AbilityExecution.with(classes[0].abilityScope) {
            assertEquals(10.0, ClassBalanceManager.scaleDamage(data, DamagePath.SKILL, 10.0))
            val hit = DamageContext(data, data, DamagePath.SKILL, DamageType.Normal, 10.0)
            hit.addBaseDamage(2.0)
            assertEquals(12.0, hit.damage)
            assertEquals(3, GrowthScaling.count(data, "swordplay", "passive-swords", 3))
            assertEquals(40, GrowthScaling.cooldown(data, 40, "freikugel"))
        }
    }
}
