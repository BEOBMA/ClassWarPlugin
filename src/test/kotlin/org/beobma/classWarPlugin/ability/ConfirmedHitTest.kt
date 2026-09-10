package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.damage.*
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.*
import org.beobma.classWarPlugin.gameClass.*
import org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler
import org.beobma.classWarPlugin.manager.DamageManager
import org.beobma.classWarPlugin.skill.*
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.Material
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.*

private class HitProbe : GameClass(), ConfirmedHitHandler {
    override val classId = "hit-probe"
    override val name = classId
    override val rank = Rank.A
    override val classItemMaterial = Material.STONE
    override val skills = emptyList<Skill>()
    override var passives = emptyList<Passive>()
    var dealt = 0
    var received = 0
    override fun onConfirmedHit(context: DamageContext) { assertSame(abilityScope, AbilityExecution.current); dealt++ }
    override fun onConfirmedDamageTaken(context: DamageContext) { received++ }
}

class ConfirmedHitTest {
    private fun participant(game: Game, ability: HitProbe): PlayerData {
        val id = UUID.randomUUID()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getUniqueId" -> id
                "getName" -> "probe"
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        return PlayerData(player, game).also { it.gameClasses += ability; game.playerDatas += it; AbilityTree.bind(it.gameClasses, it) }
    }
    @Test fun `only positive uncancelled hits dispatch under their own class scopes`() {
        val game = Game(mutableListOf(), GameConfiguration(startingItems = emptyList()), tickSource = { 0L })
        val attack = HitProbe(); val defense = HitProbe()
        val source = participant(game, attack); val target = participant(game, defense)
        AbilityTree.start(source.gameClasses); AbilityTree.start(target.gameClasses)
        fun hit() = DamageContext(source, target, DamagePath.SKILL, DamageType.Normal, 3.0)
        DamageManager.notifyConfirmedHit(hit().also { it.isCancelled = true })
        DamageManager.notifyConfirmedHit(hit().also { it.applyShieldedDamage(0.0) })
        assertEquals(0, attack.dealt); assertEquals(0, defense.received)
        DamageManager.notifyConfirmedHit(hit())
        assertEquals(1, attack.dealt); assertEquals(1, defense.received)
        assertNull(AbilityExecution.current)
        AbilityTree.end(listOf(attack), EndReason.REMOVED)
        DamageManager.notifyConfirmedHit(hit())
        assertEquals(1, attack.dealt); assertEquals(2, defense.received)
    }
}
