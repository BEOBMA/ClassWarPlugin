package org.beobma.classWarPlugin.manager

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNull

class SkillItemIdentificationTest {
    @Test fun `empty hand is not a skill and never reads item metadata`() {
        val emptyHand = object : ItemStack() {
            override fun getType() = Material.AIR
            override fun getItemMeta(): ItemMeta? = error("Empty hand metadata must not be read")
        }
        assertNull(SkillManager.getSkillId(emptyHand, UUID.randomUUID()))
    }

    @Test fun `missing metadata is not a skill and does not throw`() {
        val item = object : ItemStack() {
            override fun getType() = Material.STONE
            override fun getItemMeta(): ItemMeta? = null
        }
        assertNull(SkillManager.getSkillId(item, UUID.randomUUID()))
    }
}
