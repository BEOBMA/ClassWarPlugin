package org.beobma.classWarPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageAppearance
import org.bukkit.event.entity.EntityDamageEvent
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
import java.util.UUID

/** 피해량 텍스트 표시를 생성하고 상승·페이드·제거 수명주기를 틱 단위로 관리한다. */
object DamageIndicatorManager {
    private const val fadeStartTick = 8
    private const val risePerTick = 0.025

    private data class Indicator(
        val ownerId: UUID,
        val display: TextDisplay,
        val appearance: DamageAppearance,
        val originX: Double,
        val outlines: List<TextDisplay>,
        val slot: Int,
        var age: Int = 0,
    )

    private val miniMessage = MiniMessage.miniMessage()
    private val indicators: MutableList<Indicator> = mutableListOf()
    private val eventAppearances = java.util.WeakHashMap<EntityDamageEvent, DamageAppearance>()
    fun mark(event: EntityDamageEvent, appearance: DamageAppearance) { eventAppearances[event] = appearance }
    @Suppress("DEPRECATION") // Retain presentation for legacy damage causes from other plugins too.
    fun appearanceFor(event: EntityDamageEvent): DamageAppearance = eventAppearances[event] ?: when (event.cause) {
        EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK,
        EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.HOT_FLOOR -> DamageAppearance.BURN
        EntityDamageEvent.DamageCause.POISON -> DamageAppearance.POISON
        EntityDamageEvent.DamageCause.WITHER -> DamageAppearance.WITHER
        EntityDamageEvent.DamageCause.MAGIC, EntityDamageEvent.DamageCause.DRAGON_BREATH -> DamageAppearance.MAGIC
        EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION -> DamageAppearance.EXPLOSION
        EntityDamageEvent.DamageCause.LIGHTNING -> DamageAppearance.LIGHTNING
        else -> DamageAppearance.NORMAL
    }
    fun showExecution(entity: LivingEntity, enabled: Boolean) = show(entity, entity.health.coerceAtLeast(0.01), enabled, DamageAppearance.EXECUTION)
    private var tickingTask: BukkitTask? = null

    private val markerKey: NamespacedKey
        get() = NamespacedKey(ClassWarPlugin.instance, "damage-indicator")

    /** 이전 비정상 종료로 남은 표시 엔티티를 제거해 관리 상태를 초기화한다. */
    fun start() {
        clearOrphanedDisplays()
        ensureTickingTask()
    }

    private fun ensureTickingTask() {
        if (tickingTask != null || indicators.isEmpty()) return
        tickingTask = object : BukkitRunnable() {
            override fun run() = tickIndicators()
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }

    private fun stopTickingTaskIfIdle() {
        if (indicators.isNotEmpty()) return
        tickingTask?.cancel()
        tickingTask = null
    }

    /** [entity] 위에 양수 [damage]를 나타내는 1.5초짜리 텍스트를 생성한다. */
    fun show(entity: LivingEntity, damage: Double, enabled: Boolean, appearance: DamageAppearance = DamageAppearance.NORMAL) {
        render(entity, damage, enabled, appearance, labelOnly = false)
    }

    /** A successful status activation/consumption without a fabricated damage number. */
    fun showLabel(entity: LivingEntity, enabled: Boolean, appearance: DamageAppearance) {
        if (appearance.label.isEmpty()) return
        render(entity, 0.0, enabled, appearance, labelOnly = true)
    }

    private fun render(entity: LivingEntity, damage: Double, enabled: Boolean, appearance: DamageAppearance, labelOnly: Boolean) {
        if (!enabled || !damage.isFinite() || (!labelOnly && damage <= 0.0) || entity.isDead || (entity is Player && !entity.isOnline)) return
        // Separate simultaneous basic/status hits in camera-facing rows, bounded per target.
        val occupied = indicators.filter { it.ownerId == entity.uniqueId }
        val slot = (0..2).firstOrNull { candidate -> occupied.none { it.slot == candidate } }
            ?: occupied.first().let { oldest ->
                removeDisplay(oldest)
                indicators.remove(oldest)
                oldest.slot
            }
        val spawnLocation = entity.location.clone().add(0.0, entity.height + 0.45, 0.0)
        val display = entity.world.spawnEntity(spawnLocation, EntityType.TEXT_DISPLAY) as TextDisplay
        display.text(miniMessage.deserialize(appearance.markup(damage, labelOnly = labelOnly)))
        display.lineWidth = 260
        display.interpolationDuration = 1
        display.transformation = display.transformation.apply {
            scale.set(appearance.animatedScale(0))
            translation.y = slot * 1.8f
        }
        display.billboard = Display.Billboard.CENTER
        display.isSeeThrough = appearance == DamageAppearance.NORMAL
        display.isShadowed = true
        display.backgroundColor = Color.fromARGB(0, 0, 0, 0)
        display.textOpacity = 255.toByte()
        display.isPersistent = false
        display.persistentDataContainer.set(markerKey, PersistentDataType.BYTE, 1)
        display.transformation = display.transformation.apply { leftRotation.rotationZ(appearance.tilt) }
        val outlines = if (appearance == DamageAppearance.NORMAL) emptyList() else List(4) { index ->
            (entity.world.spawnEntity(spawnLocation, EntityType.TEXT_DISPLAY) as TextDisplay).apply {
                text(miniMessage.deserialize(appearance.markup(damage, outline = true, labelOnly = labelOnly)))
                lineWidth = 260; billboard = Display.Billboard.CENTER
                isSeeThrough = false; isShadowed = false
                backgroundColor = Color.fromARGB(0, 0, 0, 0); textOpacity = 255.toByte()
                isPersistent = false; interpolationDuration = 1
                persistentDataContainer.set(markerKey, PersistentDataType.BYTE, 1)
                transformation = display.transformation.apply {
                    val offset = 0.035f * appearance.scale
                    translation.add(if (index == 0) -offset else if (index == 1) offset else 0f,
                        if (index == 2) -offset else if (index == 3) offset else 0f, -0.012f)
                }
            }
        }
        // Upper bounds include the four outline layers: at most 400 display entities globally.
        if (indicators.size >= 80) removeDisplay(indicators.removeAt(0))
        indicators.add(Indicator(entity.uniqueId, display, appearance, spawnLocation.x, outlines, slot))
        ensureTickingTask()
    }

    /** 소유 엔티티 UUID가 [playerIds]에 포함된 활성 표시를 제거한다. */
    fun clearForPlayers(playerIds: Collection<UUID>) {
        if (playerIds.isEmpty()) return
        val iterator = indicators.iterator()
        while (iterator.hasNext()) {
            val indicator = iterator.next()
            if (indicator.ownerId !in playerIds) continue
            removeDisplay(indicator)
            iterator.remove()
        }
        stopTickingTaskIfIdle()
    }

    /** 틱 작업과 추적 중이거나 월드에 고아로 남은 모든 피해 표시를 제거한다. */
    fun shutdown() {
        tickingTask?.cancel()
        tickingTask = null
        indicators.forEach(::removeDisplay)
        indicators.clear()
        eventAppearances.clear()
        clearOrphanedDisplays()
    }

    private fun tickIndicators() {
        val iterator = indicators.iterator()
        while (iterator.hasNext()) {
            val indicator = iterator.next()
            val display = indicator.display
            val lifetimeTicks = indicator.appearance.lifetime
            if (!display.isValid || ++indicator.age >= lifetimeTicks) {
                removeDisplay(indicator)
                iterator.remove()
                continue
            }

            display.teleport(display.location.add(0.0, risePerTick, 0.0).apply { x = indicator.originX + indicator.appearance.shake(indicator.age) })
            indicator.outlines.forEach { it.teleport(display.location) }
            if (indicator.appearance != DamageAppearance.NORMAL && indicator.age <= 6) {
                display.interpolationDelay = 0
                display.transformation = display.transformation.apply { scale.set(indicator.appearance.animatedScale(indicator.age)) }
                indicator.outlines.forEach { layer ->
                    layer.interpolationDelay = 0
                    layer.transformation = layer.transformation.apply { scale.set(indicator.appearance.animatedScale(indicator.age)) }
                }
            }
            if (indicator.age >= fadeStartTick) {
                val fadeDuration = lifetimeTicks - fadeStartTick
                val remaining = lifetimeTicks - indicator.age
                val opacity = (255.0 * remaining / fadeDuration).toInt().coerceIn(0, 255)
                display.textOpacity = opacity.toByte()
                indicator.outlines.forEach { it.textOpacity = opacity.toByte() }
            }
        }
        stopTickingTaskIfIdle()
    }

    private fun removeDisplay(indicator: Indicator) { indicator.display.remove(); indicator.outlines.forEach { it.remove() } }

    private fun clearOrphanedDisplays() {
        Bukkit.getWorlds().forEach { world ->
            world.entities.asSequence()
                .filterIsInstance<TextDisplay>()
                .filter { it.persistentDataContainer.has(markerKey, PersistentDataType.BYTE) }
                .forEach { it.remove() }
        }
    }
}
