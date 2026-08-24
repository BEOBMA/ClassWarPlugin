package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute

private const val DWARF_SCALE_MULTIPLIER = 0.3
private const val DWARF_MAX_HEALTH_MULTIPLIER = 0.25

class Dwarf : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler {
    override val name = "<gray>난쟁이"
    override val rank = Rank.B
    override val classItemMaterial = Material.CHICKEN_SPAWN_EGG
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive())

    private var initialized = false
    private var originalScale: Double? = null
    private var originalMaxHealth: Double? = null

    override fun onBattleStart() {
        if (initialized) return
        initialized = true
        player.getAttribute(Attribute.SCALE)?.let { attribute ->
            originalScale = attribute.baseValue
            attribute.baseValue = (attribute.baseValue * DWARF_SCALE_MULTIPLIER).coerceAtLeast(0.1)
        }
        player.getAttribute(Attribute.MAX_HEALTH)?.let { attribute ->
            originalMaxHealth = attribute.baseValue
            attribute.baseValue = (attribute.baseValue * DWARF_MAX_HEALTH_MULTIPLIER).coerceAtLeast(1.0)
            player.health = player.health.coerceAtMost(attribute.value)
        }
        sounds.play(player, Sound.ENTITY_CHICKEN_AMBIENT, volume = 0.75f, pitch = 1.75f)
    }

    override fun onGameTimePasses() = Unit

    override fun onGameEnd() = restoreAttributes()
    override fun onPlayerDeath() = restoreAttributes()

    private fun restoreAttributes() {
        if (!initialized) return
        originalScale?.let { player.getAttribute(Attribute.SCALE)?.baseValue = it }
        originalMaxHealth?.let { maximum ->
            player.getAttribute(Attribute.MAX_HEALTH)?.let { attribute ->
                attribute.baseValue = maximum
                player.health = player.health.coerceAtMost(attribute.value)
            }
        }
        initialized = false
        originalScale = null
        originalMaxHealth = null
    }

    private class Passive : BasePassive() {
        override val name = "<bold>난쟁이"
        override val description = listOf(
            "<gray>패시브", "", "<gray>크기가 대폭 감소하고, 최대 체력이 75% 감소한다."
        )
    }
}
