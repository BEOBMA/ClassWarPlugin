package org.beobma.classWarPlugin.util

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.PlayerManager.heal
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Projectile
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal enum class ElementCastMode { MANIFEST, RELEASE, ATTUNE }

private enum class Element(
    val label: String,
    val coloredLabel: String,
    val particle: Particle,
    val displayMaterial: Material,
    val color: Color,
) {
    EARTH("흙", "<gold><bold>흙</bold></gold>", Particle.ASH, Material.COBBLESTONE, Color.fromRGB(190, 126, 54)),
    FIRE("불", "<red><bold>불</bold></red>", Particle.FLAME, Material.FIRE_CHARGE, Color.fromRGB(255, 64, 18)),
    WATER("물", "<aqua><bold>물</bold></aqua>", Particle.SPLASH, Material.PRISMARINE_SHARD, Color.fromRGB(38, 190, 255)),
    AIR("공기", "<white><bold>공기</bold></white>", Particle.CLOUD, Material.FEATHER, Color.fromRGB(235, 250, 255)),
}

private enum class Liberation {
    STORM,
    LIFE_FIELD,
    ERUPTION,
    OBSIDIAN_PRISON,
}

private data class ElementRecord(
    val element: Element,
    val mode: ElementCastMode,
    val tick: Long,
)

private data class ElementCastContext(
    val element: Element,
    val mode: ElementCastMode,
    val previous: ElementRecord?,
    val resonance: Boolean,
    val liberation: Liberation?,
) {
    val fusion: Pair<Element, Element>?
        get() = previous?.let { it.element to element }
    val fusedTargets: MutableSet<UUID> = mutableSetOf()
    val liberationTargets: MutableSet<UUID> = mutableSetOf()
    var locationLiberationTriggered = false
}

private data class TemporaryBarrierBlock(val block: Block, val original: BlockData)

/** Elementalist의 배열, 콤보 및 월드 효과를 한 인스턴스에 모아 비동기 효과도 같은 문맥을 사용하게 한다. */
internal class ElementalistRuntime(private val playerData: PlayerData) : EffectApiAccess {
    companion object {
        private const val ELEMENT_STORE_COST = 20
        private const val COMBO_WINDOW_TICKS = 60L
        private const val WATER_RANGE_FUSION_MULTIPLIER = 1.6
        private const val RESONANCE_RANGE_MULTIPLIER = 1.4
    }

    private val player = playerData.player
    private val queue = ArrayDeque<Element>()
    private val history = mutableListOf<ElementRecord>()
    private val resonanceReady = mutableSetOf<Element>()
    private val tasks = mutableSetOf<BukkitTask>()
    private val displays = mutableSetOf<Display>()
    private val temporaryBlocks = mutableListOf<TemporaryBarrierBlock>()
    private val cleanupActions = mutableSetOf<() -> Unit>()
    private var charge: Charge? = null
    private var arrayStatus: ElementArrayStatus? = null
    private var stored = false
    private var fallImmunity = false
    private var cleaned = false

    fun start() {
        cleaned = false
        queue.clear()
        repeat(8) { queue.addLast(Element.entries.random()) }
        charge = playerData.getOrCreateStatus(playerData) { Charge() }
        arrayStatus = playerData.getOrCreateStatus(playerData) { ElementArrayStatus() }
        updateArrayStatus()

        var actionBarTick = 0
        trackTimer(1L, 1L) {
            if (!player.isOnline || playerData.entityStatus.isDead) return@trackTimer false
            if (playerData.game.isPaused) return@trackTimer true
            if (player.isSneaking) charge?.addCharge(1)
            tryStoreNextElement()
            if (actionBarTick++ % 2 == 0) updateArrayStatus()
            true
        }
    }

    fun canCast(mode: ElementCastMode): Boolean {
        if (!stored || queue.isEmpty()) {
            player.sendMiniMessage("<red><bold>[!] 저장된 원소가 없습니다. 웅크려 충전 20을 모으십시오.")
            return false
        }
        val element = queue.first
        if (mode == ElementCastMode.ATTUNE && element in setOf(Element.FIRE, Element.AIR) &&
            playerData.getStatus<Fix>() != null
        ) {
            player.sendMiniMessage("<red><bold>[!] 고정 상태에서는 이동형 감응을 사용할 수 없습니다.")
            return false
        }
        return true
    }

    fun canTranspose(): Boolean {
        if (queue.size >= 2) return true
        player.sendMiniMessage("<red><bold>[!] 전위할 원소가 부족합니다.")
        return false
    }

    fun transpose() {
        if (!canTranspose()) return
        val first = queue.removeFirst()
        val second = queue.removeFirst()
        queue.addFirst(first)
        queue.addFirst(second)
        updateArrayStatus()
        particles.spawn(player, Particle.ENCHANT, count = 48, spread = 0.8, speed = 0.14)
        spawnElementDust(player.location.clone().add(0.0, 1.0, 0.0), first, 24, 0.65, 0.1, 1.1f)
        spawnElementDust(player.location.clone().add(0.0, 1.0, 0.0), second, 24, 0.65, 0.1, 1.1f)
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 10) return@trackTimer false
            val center = player.location.clone().add(0.0, 0.65 + tick * 0.1, 0.0)
            val angle = tick * 0.7
            val radius = 0.55 + sin(PI * tick / 10.0) * 0.45
            val firstPoint = center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius)
            val secondPoint = center.clone().add(cos(angle + PI) * radius, 0.0, sin(angle + PI) * radius)
            spawnElementDust(firstPoint, first, 3, 0.04, 0.015, 1.0f)
            spawnElementDust(secondPoint, second, 3, 0.04, 0.015, 1.0f)
            true
        }
        sounds.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 0.95f, pitch = 1.35f)
        sounds.play(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.68f, pitch = 1.7f)
    }

    fun cast(mode: ElementCastMode) {
        if (!canCast(mode)) return
        val element = queue.first
        val context = prepareContext(element, mode)

        when (mode) {
            ElementCastMode.MANIFEST -> manifest(context)
            ElementCastMode.RELEASE -> release(context)
            ElementCastMode.ATTUNE -> attune(context)
        }

        finishConsumption(context)
    }

    fun consumeFallImmunity(event: EntityDamageEvent): Boolean {
        if (event.cause != EntityDamageEvent.DamageCause.FALL || !fallImmunity) return false
        fallImmunity = false
        player.fallDistance = 0f
        particles.spawn(player.location, Particle.CLOUD, count = 52, spread = 0.75, speed = 0.16)
        particles.spawn(player.location, Particle.GUST, count = 3, spread = 0.42, speed = 0.05)
        spawnElementDust(player.location.clone().add(0.0, 0.25, 0.0), Element.AIR, 28, 0.65, 0.1, 0.9f)
        renderExpandingRing(player.location.clone().add(0.0, 0.12, 0.0), Element.AIR.color, 0.3, 1.85, 8, 28)
        sounds.play(player, Sound.ENTITY_BREEZE_LAND, volume = 0.8f, pitch = 1.35f)
        sounds.play(player, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.48f, pitch = 1.7f)
        return true
    }

    fun cleanup() {
        if (cleaned) return
        cleaned = true
        tasks.toList().forEach(BukkitTask::cancel)
        tasks.clear()
        cleanupActions.toList().forEach { it() }
        cleanupActions.clear()
        displays.toList().forEach { if (it.isValid) it.remove() }
        displays.clear()
        restoreTemporaryBlocks()
        arrayStatus?.remove()
        arrayStatus = null
        history.clear()
        resonanceReady.clear()
        queue.clear()
        stored = false
        fallImmunity = false
    }

    private fun tryStoreNextElement() {
        if (stored || queue.isEmpty()) return
        val resource = charge ?: return
        if (!resource.consumeCharge(ELEMENT_STORE_COST)) return
        stored = true
        updateArrayStatus()
        val element = queue.first
        val center = player.location.clone().add(0.0, 1.0, 0.0)
        particles.spawn(center, element.particle, count = 34, spread = 0.65, speed = 0.12)
        spawnElementDust(center, element, count = 30, spread = 0.55, speed = 0.09, size = 1.3f)
        renderExpandingRing(center.clone().subtract(0.0, 0.85, 0.0), element.color, 0.45, 1.65, 10, 26)
        sounds.playTo(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.8f, pitch = 1.15f)
        sounds.play(player, elementStoreSound(element), volume = 0.72f, pitch = 1.45f)
    }

    private fun prepareContext(element: Element, mode: ElementCastMode): ElementCastContext {
        val now = player.world.fullTime
        history.removeIf { now - it.tick > COMBO_WINDOW_TICKS }
        val previous = history.lastOrNull()?.takeIf {
            now - it.tick <= COMBO_WINDOW_TICKS && it.element != element
        }
        val potential = (history.takeLast(2) + ElementRecord(element, mode, now))
        val liberation = if (
            potential.size == 3 &&
            potential.last().tick - potential.first().tick <= COMBO_WINDOW_TICKS &&
            potential.map { it.element }.toSet().size == 3
        ) liberationFor(potential.map { it.element }.toSet()) else null

        val context = ElementCastContext(
            element = element,
            mode = mode,
            previous = previous,
            resonance = resonanceReady.remove(element),
            liberation = liberation,
        )
        if (previous != null) playFusionCue(previous.element, element)
        if (context.resonance) playResonanceCue(element)
        when (liberation) {
            Liberation.LIFE_FIELD -> startLifeField()
            Liberation.OBSIDIAN_PRISON -> grantObsidianShield()
            else -> Unit
        }
        return context
    }

    private fun finishConsumption(context: ElementCastContext) {
        val now = player.world.fullTime
        val previous = history.lastOrNull()
        if (previous != null && previous.element == context.element && now - previous.tick <= COMBO_WINDOW_TICKS) {
            resonanceReady += context.element
        }
        history += ElementRecord(context.element, context.mode, now)
        while (history.size > 3) history.removeFirst()

        queue.removeFirst()
        queue.addLast(Element.entries.random())
        stored = false
        tryStoreNextElement()
        updateArrayStatus()
    }

    private fun liberationFor(elements: Set<Element>): Liberation? = when (elements) {
        setOf(Element.FIRE, Element.WATER, Element.AIR) -> Liberation.STORM
        setOf(Element.EARTH, Element.WATER, Element.AIR) -> Liberation.LIFE_FIELD
        setOf(Element.EARTH, Element.FIRE, Element.AIR) -> Liberation.ERUPTION
        setOf(Element.EARTH, Element.FIRE, Element.WATER) -> Liberation.OBSIDIAN_PRISON
        else -> null
    }

    private fun updateArrayStatus() {
        val now = player.world.fullTime
        history.removeIf { now - it.tick > COMBO_WINDOW_TICKS }
        arrayStatus?.update(queue.take(5), stored, resonanceReady, history.toList(), now)
    }

    // ---------------------------------------------------------------------
    // 기본 원소 효과
    // ---------------------------------------------------------------------

    private fun manifest(context: ElementCastContext) {
        playCastCue(context.element)
        when (context.element) {
            Element.EARTH -> manifestEarth(context)
            Element.FIRE -> manifestFire(context)
            Element.WATER -> manifestWater(context)
            Element.AIR -> manifestAir(context)
        }
    }

    private fun manifestEarth(context: ElementCastContext) {
        val knockback = if (context.resonance) 1.45 else 0.95
        launchProjectile(
            context, speed = 1.05, lifetime = 3, stopOnFirstHit = true,
            size = if (context.resonance) 0.9 else 0.6,
        ) { target, location, direction ->
            target.damage(4.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            val living = target.entity as? LivingEntity
            if (context.fusion == Element.AIR to Element.EARTH) {
                living?.let { it.velocity = it.velocity.setY(-1.2) }
            } else {
                living?.velocity = direction.clone().multiply(knockback).setY(0.25)
            }
            affectTarget(context, target, location)
        }
    }

    private fun manifestFire(context: ElementCastContext) {
        val damage = 3.0 * if (context.resonance) 1.25 else 1.0
        val burnSeconds = 3 + if (context.resonance) 2 else 0
        launchProjectile(context, speed = 1.15, lifetime = 3, stopOnFirstHit = true, size = 0.55) {
                target, location, _ ->
            target.damage(damage, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            applyBurn(target, burnSeconds)
            affectTarget(context, target, location)
        }
    }

    private fun manifestWater(context: ElementCastContext) {
        val rangeMultiplier = if (context.fusion == Element.AIR to Element.WATER) {
            WATER_RANGE_FUSION_MULTIPLIER
        } else 1.0
        val slow = if (context.resonance) 15 else 10
        launchProjectile(
            context, speed = 1.45, lifetime = (3 * rangeMultiplier).toInt().coerceAtLeast(3),
            stopOnFirstHit = false, maximumHits = 2, size = 0.45,
        ) { target, location, _ ->
            target.damage(2.5, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            applySlow(target, slow, 3)
            affectTarget(context, target, location)
        }
    }

    private fun manifestAir(context: ElementCastContext) {
        val lifetime = if (context.resonance) 3 else 2
        launchProjectile(
            context, speed = 1.85, lifetime = lifetime, stopOnFirstHit = false,
            maximumHits = Int.MAX_VALUE, size = 0.7,
        ) { target, location, _ ->
            target.damage(
                2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL, armorIgnoreRatio = 0.2,
            )
            affectTarget(context, target, location)
        }
    }

    private fun release(context: ElementCastContext) {
        playCastCue(context.element)
        when (context.element) {
            Element.EARTH -> createEarthWall(context)
            Element.FIRE -> createBurningField(context)
            Element.WATER -> createWaterField(context)
            Element.AIR -> createVortex(context)
        }
    }

    private fun attune(context: ElementCastContext) {
        playCastCue(context.element)
        when (context.element) {
            Element.EARTH -> attuneEarth(context)
            Element.FIRE -> attuneFire(context)
            Element.WATER -> attuneWater(context)
            Element.AIR -> attuneAir(context)
        }
    }

    private fun attuneEarth(context: ElementCastContext) {
        val reduction = if (context.resonance) 30 else 20
        playerData.getOrCreateStatus(playerData) { WhenDamageReduction() }
            .applyStatus(duration = 3, powerSet = reduction)
        playerData.getOrCreateStatus(playerData) { MoveSpeedDecrease() }
            .applyStatus(duration = 3, powerSet = 20)
        if (context.fusion == Element.FIRE to Element.EARTH) {
            createAura(player.location.clone(), 60, 2.25, Particle.FLAME) { target, location ->
                applyBurn(target, 2)
                affectTarget(context, target, location)
            }
        }
        particles.spawn(
            player.location.clone().add(0.0, player.height / 2.0, 0.0),
            Particle.BLOCK,
            Material.STONE.createBlockData(),
            ParticleOptions.spread(58, 0.9, 0.14),
        )
        particles.spawn(player.location, Particle.DUST_PLUME, count = 22, spread = 0.65, speed = 0.13)
        spawnElementDust(player.location.clone().add(0.0, 1.0, 0.0), Element.EARTH, 36, 0.78, 0.08, 1.35f)
        renderExpandingRing(player.location.clone().add(0.0, 0.12, 0.0), Element.EARTH.color, 0.45, 2.2, 12, 30)
        sounds.play(player, Sound.BLOCK_STONE_PLACE, volume = 1.0f, pitch = 0.65f)
        sounds.play(player, Sound.ENTITY_IRON_GOLEM_REPAIR, volume = 0.65f, pitch = 0.55f)
    }

    private fun attuneFire(context: ElementCastContext) {
        val direction = horizontalDirection()
        player.velocity = direction.clone().multiply(1.55).setY(0.12)
        val burnSeconds = 1 + if (context.resonance) 2 else 0
        createFireTrail(context, burnSeconds)
        sounds.play(player, Sound.ENTITY_BLAZE_SHOOT, volume = 0.95f, pitch = 1.25f)
        sounds.play(player, Sound.ITEM_FIRECHARGE_USE, volume = 0.85f, pitch = 0.7f)
        particles.spawn(player, Particle.FLASH, count = 1)
        particles.spawn(player, Particle.SMALL_FLAME, count = 48, spread = 0.7, speed = 0.22)
    }

    private fun attuneWater(context: ElementCastContext) {
        val removable = playerData.statusAbnormalitys.filter { status ->
            status.canRemove && !status.isClassMechanic && status !is Silence && status !is Snare && status !is Fix &&
                status !is Shield && status !is Invincibility && status !is Stealth &&
                status !is MoveSpeedIncrease && status !is AttackSpeedIncrease &&
                status !is WhenDamageReduction
        }
        val removed = removable.randomOrNull()
        removed?.remove()
        if (removed == null) {
            player.sendMiniMessage("<aqua><bold>[감응]</bold> <gray>제거할 수 있는 부정적인 상태가 없습니다.")
        }
        particles.spawn(player, Particle.SPLASH, count = 85, spread = 1.0, speed = 0.2)
        particles.spawn(player, Particle.FALLING_WATER, count = 46, spread = 0.8, speed = 0.12)
        particles.spawn(player, Particle.END_ROD, count = 22, spread = 0.6, speed = 0.08)
        spawnElementDust(player.location.clone().add(0.0, 1.0, 0.0), Element.WATER, 44, 0.9, 0.12, 1.3f)
        repeat(3) { ring ->
            trackLater((ring * 2).toLong()) {
                renderExpandingRing(
                    player.location.clone().add(0.0, 0.15 + ring * 0.18, 0.0),
                    Element.WATER.color, 0.35, 1.9 + ring * 0.25, 9, 28,
                )
            }
        }
        sounds.play(player, Sound.ITEM_BUCKET_EMPTY, volume = 0.85f, pitch = 1.35f)
        sounds.play(player, Sound.BLOCK_CONDUIT_ACTIVATE, volume = 0.55f, pitch = 1.65f)
    }

    private fun attuneAir(context: ElementCastContext) {
        val multiplier = if (context.resonance) RESONANCE_RANGE_MULTIPLIER else 1.0
        val direction = horizontalDirection()
        player.velocity = direction.multiply(0.8 * multiplier).setY(1.12 * multiplier)
        player.fallDistance = 0f
        fallImmunity = true
        if (context.fusion == Element.FIRE to Element.AIR) {
            createAura(player.location.clone(), 25, 1.3, Particle.FLAME, followsPlayer = true) { target, location ->
                applyBurn(target, fireSpreadDuration(context.previous))
                affectTarget(context, target, location)
            }
        }
        particles.spawn(player, Particle.CLOUD, count = 72, spread = 0.9, speed = 0.24)
        particles.spawn(player, Particle.GUST, count = 4, spread = 0.55, speed = 0.08)
        spawnElementDust(player.location.clone().add(0.0, 0.7, 0.0), Element.AIR, 38, 0.8, 0.16, 1.05f)
        renderExpandingRing(player.location.clone().add(0.0, 0.15, 0.0), Element.AIR.color, 0.35, 2.15, 10, 30)
        sounds.play(player, Sound.ENTITY_BREEZE_JUMP, volume = 1.0f, pitch = 1.0f)
        sounds.play(player, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.7f, pitch = 1.45f)
    }

    // ---------------------------------------------------------------------
    // 영역 및 이동 효과
    // ---------------------------------------------------------------------

    private fun createEarthWall(context: ElementCastContext) {
        val direction = horizontalDirection()
        val right = Vector(-direction.z, 0.0, direction.x).normalize()
        val center = player.location.clone().add(direction.clone().multiply(3.0))
        val halfWidth = if (context.resonance) 2 else 1
        val height = if (context.resonance) 4 else 3
        val wallBlocks = mutableListOf<Block>()
        val wallDisplays = mutableListOf<BlockDisplay>()

        for (side in -halfWidth..halfWidth) {
            for (up in 0 until height) {
                val block = center.clone().add(right.clone().multiply(side.toDouble())).add(0.0, up.toDouble(), 0.0).block
                if (!block.type.isAir) continue
                temporaryBlocks += TemporaryBarrierBlock(block, block.blockData.clone())
                block.type = Material.BARRIER
                wallBlocks += block
                val wallMaterial = when (abs(block.x + block.y + block.z) % 4) {
                    0 -> Material.STONE
                    1 -> Material.DEEPSLATE
                    2 -> Material.TUFF
                    else -> Material.MOSSY_COBBLESTONE
                }
                val display = spawnBlockDisplay(block.location, wallMaterial, Vector3f(1f, 1f, 1f)).apply {
                    isGlowing = true
                    glowColorOverride = if (context.fusion == Element.FIRE to Element.EARTH) Element.FIRE.color else Element.EARTH.color
                    brightness = Display.Brightness(12, 12)
                }
                wallDisplays += display
            }
        }
        if (wallBlocks.isEmpty()) {
            player.sendMiniMessage("<red><bold>[!] 돌벽을 생성할 빈 공간이 없습니다.")
            return
        }
        val contactBox = boundingBoxOf(wallBlocks).expand(0.35)
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 80) {
                wallDisplays.forEach(::removeDisplay)
                restoreBlocks(wallBlocks.toSet())
                return@trackTimer false
            }
            if (tick % 4 == 0) {
                val particle = if (context.fusion == Element.FIRE to Element.EARTH) Particle.FLAME else Particle.ASH
                particles.spawn(center.clone().add(0.0, height / 2.0, 0.0), particle, count = 16, spread = 1.5, speed = 0.045)
                particles.spawn(
                    center.clone().add(0.0, height / 2.0, 0.0), Particle.BLOCK, Material.DEEPSLATE.createBlockData(),
                    ParticleOptions.spread(8, 1.35, 0.05),
                )
            }
            if (tick % 5 == 0) {
                playerData.radius(center, TargetType.Enemy, 4.5, false).forEach { target ->
                    if (target.entity.boundingBox.overlaps(contactBox)) {
                        affectTarget(context, target, target.entity.location)
                    }
                }
            }
            true
        }
        particles.spawn(
            center.clone().add(0.0, height / 2.0, 0.0), Particle.BLOCK, Material.STONE.createBlockData(),
            ParticleOptions.spread(64, 1.5, 0.18),
        )
        particles.spawn(center, Particle.DUST_PLUME, count = 26, spread = 1.2, speed = 0.16)
        renderExpandingRing(center.clone().add(0.0, 0.15, 0.0), Element.EARTH.color, 0.6, 2.8, 12, 34)
        sounds.play(center, Sound.BLOCK_STONE_PLACE, volume = 1.1f, pitch = 0.55f)
        sounds.play(center, Sound.ENTITY_IRON_GOLEM_HURT, volume = 0.55f, pitch = 0.65f)
    }

    private fun createBurningField(context: ElementCastContext) {
        val center = player.location.clone()
        val pauseSource = UUID.randomUUID()
        val pausedBurns = mutableMapOf<UUID, Burn>()
        lateinit var releasePausedBurns: () -> Unit
        releasePausedBurns = {
            pausedBurns.values.forEach { it.resumeDuration(pauseSource) }
            pausedBurns.clear()
            cleanupActions.remove(releasePausedBurns)
        }
        cleanupActions += releasePausedBurns
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 80) {
                releasePausedBurns()
                return@trackTimer false
            }
            if (tick % 2 == 0) {
                particles.circle(center.clone().add(0.0, 0.1, 0.0), Particle.FLAME, 3.0, 30)
                particles.circle(center.clone().add(0.0, 0.28, 0.0), Particle.SMALL_FLAME, 2.25, 24)
                particles.spawn(center, Particle.LAVA, count = 7, spread = 2.2, speed = 0.035)
            }
            if (tick % 4 == 0) {
                spawnDustRing(center.clone().add(0.0, 0.16, 0.0), 2.6 + sin(tick * 0.16) * 0.32, 34, Element.FIRE.color, 1.15f)
                particles.spawn(center.clone().add(0.0, 0.65, 0.0), Particle.LARGE_SMOKE, count = 8, spread = 2.1, speed = 0.03)
            }
            val inside = mutableSetOf<UUID>()
            playerData.radius(center, TargetType.Enemy, 3.0, false).forEach { target ->
                inside += target.entity.uniqueId
                val burn = target.getStatus<Burn>()
                if (burn != null && burn.power > 0) {
                    burn.pauseDuration(pauseSource)
                    pausedBurns[target.entity.uniqueId] = burn
                    val remaining = burn.duration ?: 1
                    (target.entity as? LivingEntity)?.fireTicks = maxOf(
                        (target.entity as? LivingEntity)?.fireTicks ?: 0,
                        remaining * 20,
                    )
                }
                affectTarget(context, target, target.entity.location)
            }
            pausedBurns.keys.filterNot { it in inside }.forEach { entityId ->
                pausedBurns.remove(entityId)?.resumeDuration(pauseSource)
            }
            true
        }
        particles.spawn(center, Particle.FLASH, count = 1)
        particles.spawn(center, Particle.FLAME, count = 72, spread = 2.3, speed = 0.2)
        renderExpandingRing(center.clone().add(0.0, 0.12, 0.0), Element.FIRE.color, 0.7, 3.3, 12, 38)
        sounds.play(center, Sound.BLOCK_FIRE_AMBIENT, volume = 0.9f, pitch = 0.75f)
        sounds.play(center, Sound.ITEM_FIRECHARGE_USE, volume = 0.85f, pitch = 0.7f)
    }

    private fun createWaterField(context: ElementCastContext) {
        val range = 3.0 * if (context.fusion == Element.AIR to Element.WATER) WATER_RANGE_FUSION_MULTIPLIER else 1.0
        val slow = if (context.resonance) 30 else 20
        val heal = if (context.resonance) 1.5 else 1.0
        val center = player.location.clone()
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 100) return@trackTimer false
            if (tick % 3 == 0) {
                particles.circle(center.clone().add(0.0, 0.1, 0.0), Particle.SPLASH, range, (range * 12).toInt())
                particles.circle(center.clone().add(0.0, 0.28, 0.0), Particle.BUBBLE_POP, range * 0.72, (range * 9).toInt())
                particles.spawn(center, Particle.FALLING_WATER, count = 14, spread = range * 0.72, speed = 0.035)
            }
            if (tick % 6 == 0) {
                spawnDustRing(
                    center.clone().add(0.0, 0.16, 0.0),
                    range * (0.72 + sin(tick * 0.12) * 0.1),
                    (range * 11).toInt(), Element.WATER.color, 1.05f,
                )
            }
            if (tick % 5 == 0) {
                playerData.radius(center, TargetType.Enemy, range, false).forEach { target ->
                    applySlow(target, slow, 1)
                    affectTarget(context, target, target.entity.location)
                }
            }
            if (tick % 20 == 0 && player.location.distanceSquared(center) <= range * range) {
                playerData.heal(heal, DamageType.Normal, playerData)
            }
            true
        }
        particles.spawn(center, Particle.SPLASH, count = 85, spread = range * 0.65, speed = 0.2)
        renderExpandingRing(center.clone().add(0.0, 0.12, 0.0), Element.WATER.color, 0.65, range + 0.35, 13, 38)
        sounds.play(center, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, volume = 0.75f, pitch = 1.2f)
        sounds.play(center, Sound.BLOCK_CONDUIT_ACTIVATE, volume = 0.55f, pitch = 1.5f)
    }

    private fun createVortex(context: ElementCastContext) {
        val direction = player.eyeLocation.direction.normalize()
        val center = player.eyeLocation.clone().add(direction.clone())
        val resonanceMultiplier = if (context.resonance) RESONANCE_RANGE_MULTIPLIER else 1.0
        val radius = 2.0 * resonanceMultiplier
        val movement = direction.clone().multiply(0.16 * resonanceMultiplier)
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 60 || center.block.type.isSolid) {
                triggerLocationLiberation(context, center)
                return@trackTimer false
            }
            center.add(movement)
            repeat(6) { arm ->
                val angle = tick * 0.46 + arm * (2.0 * PI / 6.0)
                val armRadius = radius * (0.45 + (arm % 3) * 0.16)
                val point = center.clone().add(cos(angle) * armRadius, sin(tick * 0.28 + arm) * 0.75, sin(angle) * armRadius)
                particles.spawn(point, if (context.fusion == Element.FIRE to Element.AIR) Particle.FLAME else Particle.CLOUD, count = 2, spread = 0.08, speed = 0.025)
                spawnElementDust(point, if (context.fusion == Element.FIRE to Element.AIR) Element.FIRE else Element.AIR, 1, 0.02, 0.01, 0.8f)
            }
            if (tick % 5 == 0) {
                particles.spawn(center, Particle.GUST, count = 2, spread = radius * 0.45, speed = 0.035)
                particles.spawn(center, Particle.SWEEP_ATTACK, count = 2, spread = radius * 0.4, speed = 0.04)
            }
            if (tick % 20 == 0) {
                sounds.play(center, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.35f, pitch = 1.35f)
            }
            playerData.radius(center, TargetType.Enemy, radius, false).forEach { target ->
                val living = target.entity as? LivingEntity ?: return@forEach
                val pull = center.toVector().subtract(target.entity.boundingBox.center)
                if (pull.lengthSquared() > 0.04) living.velocity = living.velocity.add(pull.normalize().multiply(0.1))
                affectTarget(context, target, target.entity.location)
            }
            true
        }
        particles.spawn(center, Particle.GUST, count = 5, spread = 0.7, speed = 0.08)
        particles.spawn(center, Particle.CLOUD, count = 56, spread = 1.3, speed = 0.2)
        sounds.play(center, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.85f, pitch = 0.8f)
    }

    private fun createFireTrail(context: ElementCastContext, burnSeconds: Int) {
        val trail = mutableListOf<Location>()
        val lastBurnTick = mutableMapOf<UUID, Int>()
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick < 12) trail += player.location.clone()
            if (tick++ >= 72) return@trackTimer false
            trail.forEachIndexed { index, location ->
                if ((tick + index) % 3 == 0) {
                    particles.spawn(location, Particle.FLAME, count = 5, spread = 0.38, speed = 0.045)
                    particles.spawn(location, Particle.SMALL_FLAME, count = 4, spread = 0.28, speed = 0.035)
                    particles.spawn(location.clone().add(0.0, 0.3, 0.0), Particle.SMOKE, count = 2, spread = 0.22, speed = 0.015)
                    spawnElementDust(location.clone().add(0.0, 0.12, 0.0), Element.FIRE, 3, 0.25, 0.02, 0.8f)
                }
                playerData.radius(location, TargetType.Enemy, 0.9, false).forEach { target ->
                    val last = lastBurnTick[target.entity.uniqueId] ?: Int.MIN_VALUE
                    if (tick - last < 15) return@forEach
                    lastBurnTick[target.entity.uniqueId] = tick
                    applyBurn(target, burnSeconds)
                    affectTarget(context, target, location)
                }
            }
            true
        }
    }

    private fun createAura(
        origin: Location,
        durationTicks: Int,
        radius: Double,
        particle: Particle,
        followsPlayer: Boolean = false,
        onTarget: (EntityData, Location) -> Unit,
    ) {
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= durationTicks) return@trackTimer false
            val center = if (followsPlayer) player.location.clone() else origin
            particles.circle(center.clone().add(0.0, 0.3, 0.0), particle, radius, 18)
            if (tick % 5 == 0) {
                playerData.radius(center, TargetType.Enemy, radius, false).forEach { onTarget(it, it.entity.location) }
            }
            true
        }
    }

    // ---------------------------------------------------------------------
    // 융합
    // ---------------------------------------------------------------------

    private fun affectTarget(context: ElementCastContext, target: EntityData, location: Location) {
        if (context.fusedTargets.add(target.entity.uniqueId)) applyFusion(context, target, location)
        applyLiberationTarget(context, target, location)
    }

    private fun applyFusion(context: ElementCastContext, target: EntityData, location: Location) {
        context.fusion?.let { (previous, current) -> renderFusionImpact(previous, current, location) }
        when (context.fusion) {
            Element.FIRE to Element.AIR -> applyBurn(target, fireSpreadDuration(context.previous))
            Element.AIR to Element.FIRE -> fusionExplosion(location)
            Element.FIRE to Element.WATER -> createSteamCloud(location)
            Element.WATER to Element.FIRE -> {
                target.statusAbnormalitys.filterIsInstance<MoveSpeedDecrease>().toList()
                    .forEach(StatusAbnormality::remove)
                target.getStatus<Burn>()?.remove()
                (target.entity as? LivingEntity)?.fireTicks = 0
                target.damage(2.0, DamageType.True, playerData, damagePath = DamagePath.SKILL)
            }
            Element.FIRE to Element.EARTH -> applyBurn(target, 2)
            Element.EARTH to Element.FIRE -> delayedEarthExplosion(location)
            Element.WATER to Element.EARTH -> applySlow(target, 50, 3)
            Element.EARTH to Element.WATER -> target.getOrCreateStatus(playerData) { WhenDamageIncreased() }
                .applyStatus(duration = 4, powerSet = 15)
            Element.WATER to Element.AIR -> target.getOrCreateStatus(playerData) { Freezing() }
                .applyStatus(duration = 3, powerSet = 1)
            Element.EARTH to Element.AIR -> (target.entity as? LivingEntity)?.addPotionEffect(
                PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true),
            )
            Element.AIR to Element.EARTH -> {
                (target.entity as? LivingEntity)?.velocity = Vector(0.0, -1.25, 0.0)
                target.getOrCreateStatus(playerData) { Fix() }.applyStatus(duration = 3, powerSet = 1)
            }
            else -> Unit
        }
    }

    private fun fusionExplosion(location: Location) {
        playerData.radius(location, TargetType.Enemy, 2.0, false).forEach {
            it.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        }
        particles.spawn(location, Particle.EXPLOSION, count = 4, spread = 0.55, speed = 0.07)
        particles.spawn(location, Particle.FLAME, count = 58, spread = 1.15, speed = 0.2)
        particles.spawn(location, Particle.GUST, count = 4, spread = 0.72, speed = 0.06)
        particles.spawn(location, Particle.LARGE_SMOKE, count = 20, spread = 0.9, speed = 0.08)
        renderExpandingRing(location.clone().add(0.0, 0.15, 0.0), Element.FIRE.color, 0.25, 2.45, 9, 32)
        sounds.play(location, Sound.ENTITY_GENERIC_EXPLODE, volume = 1.05f, pitch = 1.05f)
        sounds.play(location, Sound.ENTITY_BREEZE_WIND_BURST, volume = 0.72f, pitch = 0.72f)
    }

    private fun delayedEarthExplosion(location: Location) {
        spawnDustRing(location.clone().add(0.0, 0.12, 0.0), 1.3, 30, Element.EARTH.color, 1.15f)
        particles.spawn(location, Particle.ASH, count = 22, spread = 0.65, speed = 0.04)
        sounds.play(location, Sound.BLOCK_POINTED_DRIPSTONE_LAND, volume = 0.65f, pitch = 0.55f)
        trackLater(20L) {
            playerData.radius(location, TargetType.Enemy, 2.25, false).forEach { target ->
                target.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                (target.entity as? LivingEntity)?.velocity = (target.entity as LivingEntity).velocity.setY(0.85)
            }
            particles.spawn(location, Particle.BLOCK, Material.DIRT.createBlockData(), ParticleOptions.spread(78, 1.35, 0.22))
            particles.spawn(location, Particle.DUST_PLUME, count = 28, spread = 0.9, speed = 0.17)
            particles.spawn(location, Particle.EXPLOSION, count = 2, spread = 0.4, speed = 0.05)
            renderExpandingRing(location.clone().add(0.0, 0.12, 0.0), Element.EARTH.color, 0.35, 2.65, 10, 34)
            sounds.play(location, Sound.ENTITY_GENERIC_EXPLODE, volume = 1.05f, pitch = 0.62f)
            sounds.play(location, Sound.ENTITY_IRON_GOLEM_HURT, volume = 0.58f, pitch = 0.52f)
        }
    }

    private fun createSteamCloud(location: Location) {
        var tick = 0
        trackTimer(0L, 2L) {
            if (tick++ >= 30) return@trackTimer false
            particles.spawn(location.clone().add(0.0, 1.0, 0.0), Particle.CLOUD, count = 22, spread = 1.45, speed = 0.045)
            particles.spawn(location.clone().add(0.0, 1.0, 0.0), Particle.LARGE_SMOKE, count = 9, spread = 1.15, speed = 0.035)
            if (tick % 3 == 0) {
                particles.spawn(location.clone().add(0.0, 0.6, 0.0), Particle.FALLING_WATER, count = 8, spread = 0.9, speed = 0.03)
                spawnDustRing(location.clone().add(0.0, 0.2 + tick * 0.025, 0.0), 1.1 + tick * 0.012, 22, Color.WHITE, 0.75f)
            }
            true
        }
        particles.spawn(location, Particle.FLASH, count = 1)
        sounds.play(location, Sound.BLOCK_FIRE_EXTINGUISH, volume = 0.9f, pitch = 1.35f)
        sounds.play(location, Sound.ITEM_TRIDENT_RIPTIDE_1, volume = 0.55f, pitch = 1.65f)
    }

    private fun renderFusionImpact(previous: Element, current: Element, location: Location) {
        val center = location.clone().add(0.0, 0.45, 0.0)
        particles.spawn(center, previous.particle, count = 18, spread = 0.55, speed = 0.1)
        particles.spawn(center, current.particle, count = 18, spread = 0.55, speed = 0.1)
        spawnElementDust(center, previous, 18, 0.52, 0.09, 1.05f)
        spawnElementDust(center, current, 18, 0.52, 0.09, 1.05f)
        spawnDustRing(center, 0.72, 24, previous.color, 0.9f)
        spawnDustRing(center.clone().add(0.0, 0.1, 0.0), 1.02, 28, current.color, 0.9f)
        particles.spawn(center, Particle.ENCHANT, count = 24, spread = 0.62, speed = 0.12)
        sounds.play(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.58f, pitch = 1.55f)
    }

    private fun fireSpreadDuration(previous: ElementRecord?): Int = when (previous?.mode) {
        ElementCastMode.MANIFEST -> 3
        ElementCastMode.RELEASE, ElementCastMode.ATTUNE -> 1
        null -> 1
    }

    // ---------------------------------------------------------------------
    // 해방
    // ---------------------------------------------------------------------

    private fun applyLiberationTarget(context: ElementCastContext, target: EntityData, location: Location) {
        when (context.liberation) {
            Liberation.STORM, Liberation.ERUPTION -> triggerLocationLiberation(context, location)
            Liberation.OBSIDIAN_PRISON -> if (context.liberationTargets.add(target.entity.uniqueId)) {
                createObsidianPrison(target)
            }
            else -> Unit
        }
    }

    private fun triggerLocationLiberation(context: ElementCastContext, location: Location) {
        if (context.locationLiberationTriggered) return
        context.locationLiberationTriggered = true
        when (context.liberation) {
            Liberation.STORM -> startStorm(location.clone())
            Liberation.ERUPTION -> startEruption(location.clone())
            else -> Unit
        }
    }

    private fun startStorm(center: Location) {
        announceLiberation(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER)
        particles.spawn(center.clone().add(0.0, 1.2, 0.0), Particle.FLASH, count = 1)
        particles.spawn(center.clone().add(0.0, 1.4, 0.0), Particle.ELECTRIC_SPARK, count = 90, spread = 2.6, speed = 0.24)
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 80) return@trackTimer false
            val targets = playerData.radius(center, TargetType.Enemy, 3.5, false)
            targets.forEach { target ->
                val living = target.entity as? LivingEntity ?: return@forEach
                val pull = center.toVector().subtract(target.entity.boundingBox.center)
                if (pull.lengthSquared() > 0.04) living.velocity = living.velocity.add(pull.normalize().multiply(0.08))
                if (tick % 20 == 0) target.damage(0.25, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            }
            if (tick % 16 == 0) {
                targets.randomOrNull()?.let { target ->
                    target.entity.world.strikeLightningEffect(target.entity.location)
                    target.damage(1.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                    particles.spawn(target.entity, Particle.ELECTRIC_SPARK, count = 42, spread = 0.7, speed = 0.18)
                }
            }
            repeat(8) { arm ->
                val angle = tick * 0.34 + arm * PI / 4.0
                val armRadius = 1.45 + (arm % 3) * 0.52
                val point = center.clone().add(
                    cos(angle) * armRadius,
                    0.35 + ((tick + arm * 3) % 24) / 8.0,
                    sin(angle) * armRadius,
                )
                particles.spawn(point, Particle.ELECTRIC_SPARK, count = 2, spread = 0.08, speed = 0.035)
                particles.spawn(point, Particle.CLOUD, count = 2, spread = 0.12, speed = 0.025)
            }
            if (tick % 4 == 0) {
                particles.circle(center.clone().add(0.0, 0.18, 0.0), Particle.ELECTRIC_SPARK, 3.4, 38)
                spawnDustRing(center.clone().add(0.0, 0.22, 0.0), 2.7, 34, Element.AIR.color, 0.75f)
            }
            if (tick % 20 == 0) {
                sounds.play(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, volume = 0.52f, pitch = 1.45f)
            }
            particles.spawn(center.clone().add(0.0, 1.3, 0.0), Particle.CLOUD, count = 10, spread = 2.5, speed = 0.055)
            true
        }
    }

    private fun startLifeField() {
        announceLiberation(player.location, Sound.BLOCK_BEACON_ACTIVATE)
        val lifeGreen = Color.fromRGB(86, 255, 116)
        val lifeAqua = Color.fromRGB(78, 236, 220)
        particles.spawn(player, Particle.TOTEM_OF_UNDYING, count = 80, spread = 1.35, speed = 0.22)
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 80) return@trackTimer false
            val center = player.location.clone()
            if (tick % 3 == 0) {
                particles.circle(center.clone().add(0.0, 0.15, 0.0), Particle.HAPPY_VILLAGER, 3.5, 38)
                particles.circle(center.clone().add(0.0, 0.32, 0.0), Particle.COMPOSTER, 2.65, 30)
                particles.spawn(center, Particle.COMPOSTER, count = 12, spread = 2.2, speed = 0.04)
            }
            if (tick % 4 == 0) {
                val pulse = 2.7 + sin(tick * 0.16) * 0.65
                spawnDustRing(center.clone().add(0.0, 0.18, 0.0), pulse, 38, lifeGreen, 1.15f)
                spawnDustRing(center.clone().add(0.0, 0.34, 0.0), 3.25 - sin(tick * 0.16) * 0.35, 42, lifeAqua, 0.9f)
            }
            if (tick % 10 == 0) {
                particles.spawn(center.clone().add(0.0, 1.0, 0.0), Particle.TOTEM_OF_UNDYING, count = 18, spread = 2.1, speed = 0.11)
            }
            if (tick % 10 == 0) {
                playerData.radius(center, TargetType.Enemy, 3.5, false).forEach { applySlow(it, 20, 1) }
            }
            if (tick % 20 == 0) {
                playerData.heal(1.0, DamageType.Normal, playerData)
                sounds.play(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.38f, pitch = 1.75f)
            }
            true
        }
    }

    private fun startEruption(center: Location) {
        announceLiberation(center, Sound.ENTITY_GENERIC_EXPLODE)
        playerData.radius(center, TargetType.Enemy, 3.25, false).forEach { target ->
            target.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            (target.entity as? LivingEntity)?.velocity = (target.entity as LivingEntity).velocity.setY(1.0)
        }
        particles.spawn(center, Particle.FLASH, count = 1)
        particles.spawn(center, Particle.EXPLOSION, count = 7, spread = 1.35, speed = 0.11)
        particles.spawn(center, Particle.BLOCK, Material.MAGMA_BLOCK.createBlockData(), ParticleOptions.spread(120, 2.2, 0.26))
        particles.spawn(center, Particle.LAVA, count = 48, spread = 2.0, speed = 0.2)
        particles.spawn(center.clone().add(0.0, 1.0, 0.0), Particle.LARGE_SMOKE, count = 55, spread = 2.2, speed = 0.13)
        renderExpandingRing(center.clone().add(0.0, 0.15, 0.0), Element.FIRE.color, 0.45, 4.0, 14, 46)
        renderExpandingRing(center.clone().add(0.0, 0.22, 0.0), Element.EARTH.color, 0.8, 3.45, 12, 42)
        repeat(12) {
            val impact = center.clone().add(Random.nextDouble(-2.5, 2.5), 0.0, Random.nextDouble(-2.5, 2.5))
            spawnFallingRock(impact)
        }
        var visualTick = 0
        trackTimer(0L, 2L) {
            if (visualTick++ >= 24) return@trackTimer false
            particles.spawn(center, Particle.LAVA, count = 10, spread = 2.4, speed = 0.08)
            particles.spawn(center.clone().add(0.0, 0.5, 0.0), Particle.LARGE_SMOKE, count = 14, spread = 2.3, speed = 0.055)
            particles.spawn(
                center, Particle.BLOCK, Material.MAGMA_BLOCK.createBlockData(),
                ParticleOptions.spread(12, 2.0, 0.09),
            )
            if (visualTick % 5 == 0) sounds.play(center, Sound.BLOCK_LAVA_POP, volume = 0.45f, pitch = 0.72f)
            true
        }
    }

    private fun spawnFallingRock(impact: Location) {
        val start = impact.clone().add(0.0, Random.nextDouble(5.0, 8.0), 0.0)
        val size = Random.nextDouble(0.65, 1.05).toFloat()
        val display = spawnBlockDisplay(start, Material.MAGMA_BLOCK, Vector3f(size, size, size)).apply {
            isGlowing = true
            glowColorOverride = Element.FIRE.color
            brightness = Display.Brightness(15, 15)
            interpolationDuration = 2
        }
        var tick = 0
        trackTimer(5L, 1L) {
            if (tick++ >= 14) {
                removeDisplay(display)
                playerData.radius(impact, TargetType.Enemy, 0.9, false).forEach {
                    it.damage(1.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
                }
                particles.spawn(impact, Particle.BLOCK, Material.MAGMA_BLOCK.createBlockData(), ParticleOptions.spread(32, 0.7, 0.18))
                particles.spawn(impact, Particle.LAVA, count = 14, spread = 0.6, speed = 0.13)
                particles.spawn(impact, Particle.LARGE_SMOKE, count = 8, spread = 0.5, speed = 0.06)
                sounds.play(impact, Sound.BLOCK_STONE_BREAK, volume = 0.68f, pitch = 0.62f)
                sounds.play(impact, Sound.BLOCK_LAVA_POP, volume = 0.5f, pitch = 0.8f)
                return@trackTimer false
            }
            val progress = tick / 14.0
            val current = start.clone().add(0.0, -(start.y - impact.y) * progress, 0.0)
            display.teleport(current)
            particles.spawn(current, Particle.FLAME, count = 4, spread = 0.22, speed = 0.035)
            particles.spawn(current, Particle.SMOKE, count = 3, spread = 0.18, speed = 0.025)
            true
        }
    }

    private fun grantObsidianShield() {
        playerData.getOrCreateStatus(playerData) { Shield() }.applyStatus(duration = 4, powerDelta = 4)
        announceLiberation(player.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE)
    }

    private fun createObsidianPrison(target: EntityData) {
        val snare = target.addStatus(Snare(), playerData).also { it.applyStatus(duration = 2, powerSet = 1) }
        val living = target.entity as? LivingEntity ?: return
        val box = living.boundingBox
        val display = spawnBlockDisplay(
            box.min.toLocation(living.world), Material.OBSIDIAN,
            Vector3f((box.widthX + 0.25).toFloat(), (box.height + 0.25).toFloat(), (box.widthZ + 0.25).toFloat()),
        )
        display.transformation = display.transformation.apply {
            translation.set(-0.125f, -0.125f, -0.125f)
        }
        display.isGlowing = true
        display.glowColorOverride = Color.fromRGB(114, 42, 170)
        display.brightness = Display.Brightness(8, 12)
        particles.spawn(living, Particle.FLASH, count = 1)
        particles.spawn(living, Particle.PORTAL, count = 65, spread = 0.85, speed = 0.16)
        particles.spawn(
            living.location.clone().add(0.0, living.height / 2.0, 0.0), Particle.BLOCK,
            Material.OBSIDIAN.createBlockData(), ParticleOptions.spread(42, 0.75, 0.16),
        )
        sounds.play(living.location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume = 0.82f, pitch = 0.62f)
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 30 || !living.isValid) {
                snare.remove()
                removeDisplay(display)
                return@trackTimer false
            }
            display.teleport(living.boundingBox.min.toLocation(living.world))
            particles.spawn(living, Particle.PORTAL, count = 7, spread = 0.58, speed = 0.045)
            if (tick % 5 == 0) {
                spawnDustRing(
                    living.location.clone().add(0.0, 0.12 + (tick % 15) * 0.08, 0.0),
                    box.widthX.coerceAtLeast(0.75), 20, Color.fromRGB(126, 54, 184), 0.9f,
                )
            }
            true
        }
    }

    private fun announceLiberation(location: Location, sound: Sound) {
        particles.spawn(location.clone().add(0.0, 1.0, 0.0), Particle.FLASH, count = 2)
        particles.spawn(location.clone().add(0.0, 1.0, 0.0), Particle.END_ROD, count = 105, spread = 2.0, speed = 0.24)
        particles.spawn(location.clone().add(0.0, 1.0, 0.0), Particle.REVERSE_PORTAL, count = 75, spread = 1.65, speed = 0.18)
        val gold = Color.fromRGB(255, 198, 38)
        repeat(3) { ring ->
            trackLater((ring * 2).toLong()) {
                renderExpandingRing(
                    location.clone().add(0.0, 0.12 + ring * 0.08, 0.0),
                    gold, 0.5 + ring * 0.2, 3.4 + ring * 0.45, 14, 42,
                )
            }
        }
        sounds.play(location, sound, volume = 1.25f, pitch = 0.72f)
        sounds.play(location, Sound.BLOCK_END_PORTAL_SPAWN, volume = 0.68f, pitch = 1.25f)
        sounds.play(location, Sound.BLOCK_BEACON_ACTIVATE, volume = 0.82f, pitch = 0.58f)
    }

    // ---------------------------------------------------------------------
    // 공통 도우미
    // ---------------------------------------------------------------------

    private fun launchProjectile(
        context: ElementCastContext,
        speed: Double,
        lifetime: Int,
        stopOnFirstHit: Boolean,
        maximumHits: Int = 1,
        size: Double,
        onHit: (EntityData, Location, Vector) -> Unit,
    ) {
        ElementProjectile(
            location = player.eyeLocation.clone(), context = context, speedValue = speed,
            lifetime = lifetime, stopOnFirstHit = stopOnFirstHit, maximumHits = maximumHits,
            hitboxSize = size, onHitAction = onHit,
        ).spawnProjectile(playerData)
    }

    private inner class ElementProjectile(
        override var location: Location,
        private val context: ElementCastContext,
        private val speedValue: Double,
        private val lifetime: Int,
        private val stopOnFirstHit: Boolean,
        private val maximumHits: Int,
        private val hitboxSize: Double,
        private val onHitAction: (EntityData, Location, Vector) -> Unit,
    ) : Projectile() {
        override var targetType = TargetType.Enemy
        override var speed = speedValue
        override var isWallHit = true
        override var isPlayerHit = true
        override val isPlayerHitRemove = stopOnFirstHit
        override var time: Int? = lifetime
        override var xSize = hitboxSize
        override var ySize = hitboxSize
        override var zSize = hitboxSize
        override val itemDisplayItem = ItemStack(context.element.displayMaterial)
        private val hitIds = mutableSetOf<UUID>()
        private val direction = location.direction.normalize()

        override fun onItemDisplaySpawn(display: ItemDisplay, location: Location) {
            display.billboard = Display.Billboard.CENTER
            display.isGlowing = true
            display.glowColorOverride = context.element.color
            display.brightness = Display.Brightness(15, 15)
            val scale = when (context.element) {
                Element.EARTH -> 1.0f
                Element.FIRE -> 0.82f
                Element.WATER -> 0.72f
                Element.AIR -> 0.62f
            }
            display.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(scale, scale, scale), Quaternionf())
        }

        override fun onProjectileMove(location: Location) {
            particles.spawn(location, context.element.particle, count = 5, spread = 0.2, speed = 0.035)
            spawnElementDust(location, context.element, count = 3, spread = 0.12, speed = 0.015, size = 0.8f)
            when (context.element) {
                Element.EARTH -> particles.spawn(
                    location, Particle.BLOCK, Material.DIRT.createBlockData(),
                    ParticleOptions.spread(3, 0.16, 0.045),
                )
                Element.FIRE -> {
                    particles.spawn(location, Particle.SMALL_FLAME, count = 4, spread = 0.18, speed = 0.035)
                    particles.spawn(location, Particle.SMOKE, count = 2, spread = 0.12, speed = 0.01)
                }
                Element.WATER -> particles.spawn(location, Particle.FALLING_WATER, count = 3, spread = 0.16, speed = 0.02)
                Element.AIR -> {
                    particles.spawn(location, Particle.SWEEP_ATTACK)
                    particles.spawn(location, Particle.GUST, count = 1, spread = 0.06, speed = 0.01)
                }
            }
            if (context.fusion == Element.FIRE to Element.AIR || context.fusion == Element.FIRE to Element.EARTH) {
                particles.spawn(location, Particle.FLAME, count = 4, spread = 0.16, speed = 0.035)
            }
        }

        override fun onProjectileEntityHit(hitEntityData: EntityData, location: Location) {
            if (hitIds.size >= maximumHits || !hitIds.add(hitEntityData.entity.uniqueId)) return
            renderElementImpact(context.element, location, entityHit = true)
            onHitAction(hitEntityData, location, direction)
        }

        override fun onProjectileBlockHit(hitBlock: Block, location: Location) {
            triggerLocationLiberation(context, location)
            renderElementImpact(context.element, location, entityHit = false)
        }
    }

    private fun applyBurn(target: EntityData, seconds: Int) {
        target.getOrCreateStatus(playerData) { Burn() }.applyStatus(duration = seconds, powerSet = 1)
    }

    private fun applySlow(target: EntityData, percent: Int, seconds: Int) {
        target.getOrCreateStatus(playerData) { MoveSpeedDecrease() }
            .applyStatus(duration = seconds, powerSet = percent)
    }

    private fun horizontalDirection(): Vector {
        val direction = player.eyeLocation.direction.setY(0.0)
        return if (direction.lengthSquared() < 1.0E-8) Vector(0.0, 0.0, 1.0) else direction.normalize()
    }

    private fun spawnElementDust(
        location: Location,
        element: Element,
        count: Int,
        spread: Double,
        speed: Double,
        size: Float = 1.0f,
    ) {
        particles.spawn(
            location,
            Particle.DUST,
            Particle.DustOptions(element.color, size),
            ParticleOptions.spread(count, spread, speed),
        )
    }

    private fun spawnDustRing(center: Location, radius: Double, points: Int, color: Color, size: Float = 1.0f) {
        val dust = Particle.DustOptions(color, size)
        repeat(points) { index ->
            val angle = 2.0 * PI * index / points
            particles.spawn(
                center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius),
                Particle.DUST,
                dust,
            )
        }
    }

    private fun renderExpandingRing(
        center: Location,
        color: Color,
        startRadius: Double,
        endRadius: Double,
        durationTicks: Int,
        points: Int,
    ) {
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= durationTicks) return@trackTimer false
            val progress = tick.toDouble() / durationTicks
            spawnDustRing(center, startRadius + (endRadius - startRadius) * progress, points, color, 1.1f)
            true
        }
    }

    private fun renderCastSigil(element: Element) {
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 12 || !player.isOnline) return@trackTimer false
            val center = player.location.clone().add(0.0, 0.08, 0.0)
            if (tick % 2 == 0) {
                spawnDustRing(center, 0.82 + tick * 0.025, 26, element.color, 1.05f)
                spawnDustRing(center.clone().add(0.0, 0.09, 0.0), 1.18 - tick * 0.018, 22, Color.WHITE, 0.72f)
            }
            repeat(4) { arm ->
                val angle = tick * 0.42 + arm * PI / 2.0
                val radius = 0.38 + tick * 0.045
                val point = center.clone().add(cos(angle) * radius, tick * 0.12, sin(angle) * radius)
                spawnElementDust(point, element, 2, 0.04, 0.01, 0.85f)
                particles.spawn(point, element.particle, count = 1, spread = 0.02, speed = 0.01)
            }
            true
        }
    }

    private fun renderElementImpact(element: Element, location: Location, entityHit: Boolean) {
        particles.spawn(location, element.particle, count = 38, spread = 0.72, speed = 0.14)
        spawnElementDust(location, element, count = 30, spread = 0.62, speed = 0.12, size = 1.25f)
        when (element) {
            Element.EARTH -> {
                particles.spawn(
                    location, Particle.BLOCK, Material.DEEPSLATE.createBlockData(),
                    ParticleOptions.spread(28, 0.65, 0.16),
                )
                particles.spawn(location, Particle.DUST_PLUME, count = 12, spread = 0.48, speed = 0.1)
            }
            Element.FIRE -> {
                particles.spawn(location, Particle.SMALL_FLAME, count = 30, spread = 0.65, speed = 0.16)
                particles.spawn(location, Particle.LARGE_SMOKE, count = 12, spread = 0.5, speed = 0.06)
                particles.spawn(location, Particle.FLASH, count = 1)
            }
            Element.WATER -> {
                particles.spawn(location, Particle.FALLING_WATER, count = 32, spread = 0.7, speed = 0.12)
                particles.spawn(location, Particle.BUBBLE_POP, count = 22, spread = 0.55, speed = 0.11)
            }
            Element.AIR -> {
                particles.spawn(location, Particle.GUST, count = 3, spread = 0.32, speed = 0.04)
                particles.spawn(location, Particle.SWEEP_ATTACK, count = 5, spread = 0.55, speed = 0.08)
                particles.spawn(location, Particle.CLOUD, count = 38, spread = 0.8, speed = 0.18)
            }
        }
        if (entityHit) particles.spawn(location, Particle.CRIT, count = 18, spread = 0.45, speed = 0.13)
        val impactSound = when (element) {
            Element.EARTH -> Sound.BLOCK_DEEPSLATE_BREAK
            Element.FIRE -> Sound.ITEM_FIRECHARGE_USE
            Element.WATER -> Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED
            Element.AIR -> Sound.ENTITY_BREEZE_WIND_BURST
        }
        sounds.play(location, impactSound, volume = if (entityHit) 0.95f else 0.72f, pitch = if (entityHit) 0.82f else 1.18f)
    }

    private fun elementStoreSound(element: Element): Sound = when (element) {
        Element.EARTH -> Sound.BLOCK_STONE_PLACE
        Element.FIRE -> Sound.ITEM_FIRECHARGE_USE
        Element.WATER -> Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE
        Element.AIR -> Sound.ENTITY_BREEZE_IDLE_AIR
    }

    private fun playCastCue(element: Element) {
        val (primary, secondary) = when (element) {
            Element.EARTH -> Sound.BLOCK_DEEPSLATE_BREAK to Sound.BLOCK_STONE_PLACE
            Element.FIRE -> Sound.ENTITY_BLAZE_SHOOT to Sound.ITEM_FIRECHARGE_USE
            Element.WATER -> Sound.ITEM_TRIDENT_RIPTIDE_1 to Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE
            Element.AIR -> Sound.ENTITY_BREEZE_SHOOT to Sound.ENTITY_BREEZE_WIND_BURST
        }
        sounds.play(player, primary, volume = 1.0f, pitch = 0.95f)
        sounds.play(player, secondary, volume = 0.62f, pitch = 1.45f)
        particles.spawn(player, element.particle, count = 38, spread = 0.7, speed = 0.13)
        spawnElementDust(player.location.clone().add(0.0, 1.0, 0.0), element, 28, 0.62, 0.1, 1.2f)
        renderCastSigil(element)
    }

    private fun playFusionCue(previous: Element, current: Element) {
        sounds.playTo(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 1.0f, pitch = 1.12f)
        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume = 0.58f, pitch = 1.72f)
        var tick = 0
        trackTimer(0L, 1L) {
            if (tick++ >= 14) return@trackTimer false
            val center = player.location.clone().add(0.0, 1.05, 0.0)
            val radius = 0.55 + sin(PI * tick / 14.0) * 0.85
            repeat(3) { arm ->
                val angle = tick * 0.5 + arm * 2.0 * PI / 3.0
                val y = sin(angle * 1.5) * 0.55
                val first = center.clone().add(cos(angle) * radius, y, sin(angle) * radius)
                val second = center.clone().add(cos(-angle) * radius, -y, sin(-angle) * radius)
                spawnElementDust(first, previous, 2, 0.04, 0.01, 1.0f)
                spawnElementDust(second, current, 2, 0.04, 0.01, 1.0f)
            }
            if (tick % 3 == 0) particles.spawn(center, Particle.REVERSE_PORTAL, count = 9, spread = 0.5, speed = 0.05)
            true
        }
    }

    private fun playResonanceCue(element: Element) {
        sounds.playTo(player, Sound.BLOCK_BEACON_POWER_SELECT, volume = 1.0f, pitch = 1.35f)
        sounds.play(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume = 0.8f, pitch = 1.75f)
        val center = player.location.clone().add(0.0, 0.12, 0.0)
        repeat(3) { ring ->
            trackLater((ring * 2).toLong()) {
                renderExpandingRing(center, element.color, 0.5 + ring * 0.18, 2.2 + ring * 0.25, 10, 30)
            }
        }
        particles.spawn(player, Particle.ENCHANT, count = 70, spread = 1.0, speed = 0.19)
        particles.spawn(player, Particle.END_ROD, count = 28, spread = 0.72, speed = 0.12)
        spawnElementDust(player.location.clone().add(0.0, 1.0, 0.0), element, 42, 0.85, 0.16, 1.45f)
    }

    private fun spawnBlockDisplay(location: Location, material: Material, scale: Vector3f): BlockDisplay {
        val display = location.world.spawn(location, BlockDisplay::class.java)
        display.block = material.createBlockData()
        display.isPersistent = false
        TemporaryDisplayManager.mark(display, player.uniqueId)
        display.transformation = Transformation(Vector3f(), Quaternionf(), scale, Quaternionf())
        displays += display
        return display
    }

    private fun removeDisplay(display: Display) {
        displays.remove(display)
        if (display.isValid) display.remove()
    }

    private fun boundingBoxOf(blocks: List<Block>): BoundingBox {
        val minX = blocks.minOf { it.x }.toDouble()
        val minY = blocks.minOf { it.y }.toDouble()
        val minZ = blocks.minOf { it.z }.toDouble()
        val maxX = blocks.maxOf { it.x }.toDouble() + 1.0
        val maxY = blocks.maxOf { it.y }.toDouble() + 1.0
        val maxZ = blocks.maxOf { it.z }.toDouble() + 1.0
        return BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun restoreBlocks(targets: Set<Block>) {
        val iterator = temporaryBlocks.iterator()
        while (iterator.hasNext()) {
            val temporary = iterator.next()
            if (temporary.block !in targets) continue
            if (temporary.block.type == Material.BARRIER) temporary.block.blockData = temporary.original
            iterator.remove()
        }
    }

    private fun restoreTemporaryBlocks() {
        temporaryBlocks.forEach { temporary ->
            if (temporary.block.type == Material.BARRIER) temporary.block.blockData = temporary.original
        }
        temporaryBlocks.clear()
    }

    private fun trackTimer(delay: Long, period: Long, body: () -> Boolean): BukkitTask {
        lateinit var task: BukkitTask
        task = object : BukkitRunnable() {
            override fun run() {
                if (cleaned || !body()) {
                    tasks.remove(task)
                    cancel()
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, delay, period)
        tasks += task
        playerData.trackTask(task)
        return task
    }

    private fun trackLater(delay: Long, body: () -> Unit): BukkitTask {
        lateinit var task: BukkitTask
        task = object : BukkitRunnable() {
            override fun run() {
                tasks.remove(task)
                if (!cleaned) body()
            }
        }.runTaskLater(ClassWarPlugin.instance, delay)
        tasks += task
        playerData.trackTask(task)
        return task
    }
}

private class ElementArrayStatus : StatusAbnormality() {
    private var elements: List<Element> = emptyList()
    private var stored = false
    private var resonances: Set<Element> = emptySet()
    private var history: List<ElementRecord> = emptyList()
    private var currentTick = 0L

    override val name = "<gradient:#d9a441:#57d9ff><bold>원소 배열</bold></gradient><gray>"
    override val description = listOf(
        "<gray>저장된 원소, 사용 기록과 앞으로 등장할 원소 순서이다.",
        "<light_purple>✦</light_purple><gray>: 공명 강화</gray>  <aqua>◇</aqua><gray>: 공명</gray>  " +
            "<yellow>◆</yellow><gray>: 융합</gray>  <gold>★</gold><gray>: 해방</gray>",
    )
    override val canRemove = true
    override val isClassMechanic = true
    override var power = 1
    override var maxPower: Int? = 1
    override val showPower = false
    override val showMaxPower = false
    override var duration: Int? = null

    fun update(
        elements: List<Element>,
        stored: Boolean,
        resonances: Set<Element>,
        history: List<ElementRecord>,
        currentTick: Long,
    ) {
        this.elements = elements.toList()
        this.stored = stored
        this.resonances = resonances.toSet()
        this.history = history.toList()
        this.currentTick = currentTick
        updatePower(1)
    }

    override fun actionBarText(): String {
        val queue = elements.mapIndexed { index, element ->
            val marker = buildString {
                if (element in resonances) append("<light_purple>✦</light_purple>")
                val previous = history.lastOrNull()
                if (previous != null && currentTick - previous.tick <= 60L) {
                    if (previous.element == element) append("<aqua>◇</aqua>")
                    else append("<yellow>◆</yellow>")
                }
                val liberationHistory = history.takeLast(2)
                if (
                    liberationHistory.size == 2 &&
                    currentTick - liberationHistory.first().tick <= 60L &&
                    (liberationHistory.map { it.element } + element).toSet().size == 3
                ) append("<gold>★</gold>")
            }
            val markedElement = "${element.coloredLabel}$marker"
            if (index == 0 && stored) {
                "<white><bold>[</bold></white>$markedElement<white><bold>]</bold></white>"
            } else markedElement
        }.joinToString(" <dark_gray>→</dark_gray> ")

        val activeHistory = history.filter { currentTick - it.tick <= 60L }
        val used = if (activeHistory.isEmpty()) {
            ""
        } else {
            val remainingTicks = (60L - (currentTick - activeHistory.first().tick)).coerceAtLeast(0L)
            val remainingTenths = ((remainingTicks + 1L) / 2L).toInt()
            val remaining = "${remainingTenths / 10}.${remainingTenths % 10}"
            val chain = activeHistory.joinToString(" <dark_gray>→</dark_gray> ") { it.element.coloredLabel }
            " <dark_gray>│</dark_gray> <white><bold>사용</bold></white> $chain <gray>${remaining}초</gray>"
        }
        val legend = " <dark_gray>│</dark_gray> <light_purple>✦강화</light_purple> <aqua>◇공명</aqua> " +
            "<yellow>◆융합</yellow> <gold>★해방</gold>"
        return "$name: $queue$used$legend"
    }
}
