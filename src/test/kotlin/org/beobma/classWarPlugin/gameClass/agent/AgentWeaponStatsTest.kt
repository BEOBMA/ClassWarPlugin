package org.beobma.classWarPlugin.gameClass.agent

import com.google.common.collect.ArrayListMultimap
import org.beobma.classWarPlugin.gameClass.list.Agent
import org.bukkit.Material
import kotlin.test.*

class AgentWeaponStatsTest {
    @Test fun `all appearances use iron sword attack damage while retaining their speed`() {
        val iron = ArrayListMultimap.create<String, Double>().apply { put("damage", 5.0); put("speed", -2.4) }
        val forms = listOf(8.0 to -3.1, 5.0 to -2.4, 6.0 to -2.4, 3.0 to -2.4,
            5.0 to -3.4, 7.0 to -2.4, 8.0 to -2.9, null to null, 0.0 to -1.0)
        forms.forEach { (damage, speed) ->
            val appearance = ArrayListMultimap.create<String, Double>().apply {
                damage?.let { put("damage", it) }; speed?.let { put("speed", it) }
                put("other", .25)
            }
            val normalized = AgentWeaponStats.ironSwordDamageModifiers(appearance, iron, "damage")
            assertEquals(listOf(5.0), normalized.get("damage").toList())
            assertEquals(speed?.let { listOf(it) } ?: emptyList(), normalized.get("speed").toList())
            assertEquals(listOf(.25), normalized.get("other").toList())
            assertEquals(damage?.let { listOf(it) } ?: emptyList(), appearance.get("damage").toList())
        }
    }
    @Test fun `normalization does not stack damage modifiers when applied repeatedly`() {
        val appearance = ArrayListMultimap.create<String, Double>().apply { put("damage", 7.0); put("damage", 8.0) }
        val iron = ArrayListMultimap.create<String, Double>().apply { put("damage", 5.0) }
        val first = AgentWeaponStats.ironSwordDamageModifiers(appearance, iron, "damage")
        val second = AgentWeaponStats.ironSwordDamageModifiers(first, iron, "damage")
        assertEquals(listOf(5.0), second.get("damage").toList())
        assertEquals(listOf(7.0, 8.0), appearance.get("damage").toList())
    }
    @Test fun `underlying item is always a sword rather than a mace or chain`() {
        assertEquals(Material.IRON_SWORD, Agent().weapon.material)
    }
}
