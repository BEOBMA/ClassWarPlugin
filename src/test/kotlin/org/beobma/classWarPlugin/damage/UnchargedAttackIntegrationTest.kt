package org.beobma.classWarPlugin.damage

import org.beobma.classWarPlugin.ability.AbilityTree
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.game.GameConfiguration
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.manager.DamageManager
import org.beobma.classWarPlugin.skill.Passive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.*

class UnchargedAttackIntegrationTest {
    private class Probe : GameClass(), OnHitHandler, WhenHitHandler, ConfirmedHitHandler {
        override val classId = "general-person"
        override val name = "probe"
        override val rank = Rank.A
        override val classItemMaterial = Material.IRON_SWORD
        override val skills = emptyList<Skill>()
        override var passives = emptyList<Passive>()
        var restrictions = 0
        var procs = 0
        var confirmed = 0
        var defended = 0
        var cancelWeapon = false
        override fun onHit(context: DamageContext) = context.addDamageDealtMultiplier(0.9)
        override fun onAttackDamage(context: DamageContext) { restrictions++; if (cancelWeapon) context.isCancelled = true }
        override fun onAttackHit(context: DamageContext) { procs++; context.addBaseDamage(2.0) }
        override fun whenHit(context: DamageContext) = context.addDamageTakenMultiplier(0.8)
        override fun onConfirmedHit(context: DamageContext) { confirmed++ }
        override fun onConfirmedDamageTaken(context: DamageContext) { defended++ }
    }

    private fun participant(): Pair<PlayerData, Probe> {
        val id = UUID.randomUUID()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { self, method, args ->
            when (method.name) {
                "getUniqueId" -> id
                "getName" -> "probe"
                "getHealth" -> 20.0
                "getAttribute" -> null
                "isDead" -> false
                "isValid", "isOnline" -> true
                "getScoreboardTags" -> emptySet<String>()
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "AttackProbe"
                else -> error("Unexpected Player.${method.name}")
            }
        } as Player
        val game = Game(mutableListOf(), GameConfiguration(startingItems = emptyList()), tickSource = { 0L })
        val data = PlayerData(player, game)
        val probe = Probe()
        data.gameClasses += probe; game.playerDatas += data
        AbilityTree.bind(data.gameClasses, data)
        return data to probe
    }

    @Test fun `partial strike retains sub-one damage modifiers and defense but skips attack procs`() {
        val (data, probe) = participant()
        val hit = AttackEffectContext.with(data.uniqueId, false) {
            DamageContext(data, data, DamagePath.BASIC_ATTACK, DamageType.Normal, 0.5, weaponClassId = "general-person")
        }
        assertFalse(hit.attackEffectsAllowed)
        assertTrue(DamageManager.process(hit))
        assertFalse(hit.isCancelled)
        assertEquals(0.5 * 0.9 * 0.8 * 0.6, hit.damage, 1e-9)
        assertEquals(1, probe.restrictions)
        assertEquals(0, probe.procs)
        DamageManager.notifyConfirmedHit(hit)
        assertEquals(0, probe.confirmed)
        assertEquals(1, probe.defended)
    }

    @Test fun `full strike still applies hit and confirmed effects`() {
        val (data, probe) = participant()
        val hit = DamageContext(data, data, DamagePath.BASIC_ATTACK, DamageType.Normal, 5.0, weaponClassId = "general-person")
        assertTrue(DamageManager.process(hit))
        assertEquals(7.0 * 0.9 * 0.8 * 0.6, hit.damage, 1e-9)
        DamageManager.notifyConfirmedHit(hit)
        assertEquals(1, probe.procs)
        assertEquals(1, probe.confirmed)
        assertEquals(1, probe.defended)
    }

    @Test fun `weapon prohibitions still reject an uncharged strike`() {
        val (data, probe) = participant()
        probe.cancelWeapon = true
        val hit = DamageContext(data, data, DamagePath.BASIC_ATTACK, DamageType.Normal, 5.0, weaponClassId = "general-person", attackEffectsAllowed = false)
        assertFalse(DamageManager.process(hit))
        assertTrue(hit.isCancelled)
        assertEquals(0, probe.procs)
    }

    @Test fun `attack disabling statuses cannot be bypassed by partial charge`() {
        val (data, _) = participant()
        val statuses = listOf(org.beobma.classWarPlugin.status.list.Stun(),
            org.beobma.classWarPlugin.status.list.Freezing(), org.beobma.classWarPlugin.status.list.Enchantment())
        statuses.forEach { status ->
            val hit = DamageContext(data, data, DamagePath.BASIC_ATTACK, DamageType.Normal, 5.0, weaponClassId = "general-person", attackEffectsAllowed = false)
            (status as org.beobma.classWarPlugin.status.handler.StatusOnHitHandler).onAttackDamage(hit)
            assertTrue(hit.isCancelled)
        }
    }
}
