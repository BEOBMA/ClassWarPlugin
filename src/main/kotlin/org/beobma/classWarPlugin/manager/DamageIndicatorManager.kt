package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

object DamageIndicatorManager {
    private const val lifetimeTicks = 30
    private const val fadeStartTick = 8
    private const val risePerTick = 0.025

    private data class Indicator(
        val ownerId: UUID,
        val display: TextDisplay,
        var age: Int = 0,
    )

    private val miniMessage = MiniMessage.miniMessage()
    private val numberFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))
    private val indicators: MutableList<Indicator> = mutableListOf()
    private var tickingTask: BukkitTask? = null

    private val markerKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "damage-indicator")

    fun start() {
        clearOrphanedDisplays()
        if (tickingTask != null) return
        tickingTask = object : BukkitRunnable() {
            override fun run() = tickIndicators()
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }

    fun show(entity: LivingEntity, damage: Double, enabled: Boolean) {
        if (!enabled || damage <= 0.0 || entity.isDead || (entity is Player && !entity.isOnline)) return
        val spawnLocation = entity.location.clone().add(
            Random.nextDouble(-0.35, 0.35),
            entity.height + 0.45,
            Random.nextDouble(-0.35, 0.35),
        )
        val display = entity.world.spawnEntity(spawnLocation, EntityType.TEXT_DISPLAY) as TextDisplay
        display.text(miniMessage.deserialize("<red><bold>-${numberFormat.format(damage)}</bold></red>"))
        display.billboard = Display.Billboard.CENTER
        display.isSeeThrough = true
        display.isShadowed = true
        display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
        display.textOpacity = 255.toByte()
        display.isPersistent = false
        display.persistentDataContainer.set(markerKey, PersistentDataType.BYTE, 1)
        indicators.add(Indicator(entity.uniqueId, display))
    }

    fun clearForPlayers(playerIds: Collection<UUID>) {
        if (playerIds.isEmpty()) return
        val iterator = indicators.iterator()
        while (iterator.hasNext()) {
            val indicator = iterator.next()
            if (indicator.ownerId !in playerIds) continue
            indicator.display.remove()
            iterator.remove()
        }
    }

    fun shutdown() {
        tickingTask?.cancel()
        tickingTask = null
        indicators.forEach { it.display.remove() }
        indicators.clear()
        clearOrphanedDisplays()
    }

    private fun tickIndicators() {
        val iterator = indicators.iterator()
        while (iterator.hasNext()) {
            val indicator = iterator.next()
            val display = indicator.display
            if (!display.isValid || ++indicator.age >= lifetimeTicks) {
                display.remove()
                iterator.remove()
                continue
            }

            display.teleport(display.location.add(0.0, risePerTick, 0.0))
            if (indicator.age >= fadeStartTick) {
                val fadeDuration = lifetimeTicks - fadeStartTick
                val remaining = lifetimeTicks - indicator.age
                val opacity = (255.0 * remaining / fadeDuration).toInt().coerceIn(0, 255)
                display.textOpacity = opacity.toByte()
            }
        }
    }

    private fun clearOrphanedDisplays() {
        Bukkit.getWorlds().forEach { world ->
            world.entities.asSequence()
                .filterIsInstance<TextDisplay>()
                .filter { it.persistentDataContainer.has(markerKey, PersistentDataType.BYTE) }
                .forEach { it.remove() }
        }
    }
}
