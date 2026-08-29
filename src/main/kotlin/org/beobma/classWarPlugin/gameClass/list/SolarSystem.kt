package org.beobma.classWarPlugin.gameClass.list

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.ParticleOptions
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.GameClassManager.toWeaponItemStack
import org.beobma.classWarPlugin.manager.InventoryManager.skillDyeMaterial
import org.beobma.classWarPlugin.manager.ItemDescriptionManager
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.SkillManager.markSkillItem
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.manager.TemporaryDisplayManager
import org.beobma.classWarPlugin.manager.AttackableObjectManager
import org.beobma.classWarPlugin.manager.UtilManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Color
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.BoundingBox
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

class SolarSystem : GameClass(), GameStatusHandler, GameEndHandler, OnHitHandler {
    private val sol = Sol()
    private val luna = Luna()
    private val mercurius = Mercurius()
    private val venus = Venus()
    private val terra = Terra()
    private val mars = Mars().asSolarCopy()
    private val jupiter = Jupiter()
    private val saturnus = Saturnus()
    private val uranus = Uranus()
    private val neptune = Neptune()
    private val pluto = Pluto().asSolarCopy()
    private val planets: List<PlanetClass> =
        listOf(sol, luna, mercurius, venus, terra, mars, jupiter, saturnus, uranus, neptune, pluto)

    override val name = "<gray>태양계"
    override val rank = Rank.L
    override val classItemMaterial = Material.AMETHYST_BLOCK
    override var skills: List<Skill> = planets.flatMap { it.skills }
    override var passives: List<BasePassive> = listOf(Passive())

    private val bodies = mutableListOf<OrbitBody>()
    private val destroyedUntil = mutableMapOf<Class<out PlanetClass>, Long>()
    private val planetStatuses = mutableListOf<SolarPlanetStatus>()
    private val bodyRegistrations = mutableListOf<AttackableObjectManager.Registration>()
    private var orbitTask: BukkitTask? = null

    override fun onBattleStart() {
        clearBodies()
        destroyedUntil.clear()
        planets.forEach { planet ->
            planet.bindSolarPower { isPlanetActive(planet.javaClass) }
            planet.inject(playerData)
            planet.skills.forEach { it.inject(playerData) }
            planet.passives.forEach { it.inject(playerData) }
        }
        saturnus.useSolarSystemOrbit()
        planets.filterNot(::hasDirectCopy).forEach { planet ->
            planet.passives.filterIsInstance<GameStatusHandler>().forEach { it.onBattleStart() }
            (planet as? GameStatusHandler)?.onBattleStart()
        }
        createPlanetStatuses()
        createBodies()
        ensureUranusEquipment()
        startOrbitTask()
    }

    override fun onGameTimePasses() = Unit

    override fun onGameEnd() {
        clearBodies()
        clearPlanetStatuses()
        planets.filterIsInstance<GameEndHandler>().forEach { it.onGameEnd() }
    }

    override fun onHit(context: DamageContext) =
        activeDelegates<OnHitHandler>().forEach { it.onHit(context) }

    override fun onAttackHit(context: DamageContext) =
        activeDelegates<OnHitHandler>().forEach { it.onAttackHit(context) }

    override fun onSkillAttackHit(context: DamageContext) =
        activeDelegates<OnHitHandler>().forEach { it.onSkillAttackHit(context) }

    override fun onStatusEffectAttackHit(context: DamageContext) =
        activeDelegates<OnHitHandler>().forEach { it.onStatusEffectAttackHit(context) }

    internal fun <T : PlanetClass> isPlanetActive(type: Class<T>): Boolean {
        val planet = planets.firstOrNull(type::isInstance) ?: return false
        if (hasDirectCopy(planet)) return true
        return (destroyedUntil[planet.javaClass] ?: 0L) <= currentTick()
    }

    private inline fun <reified T> activeDelegates(): List<T> = planets.asSequence()
        .filterNot(::hasDirectCopy)
        .filter { isPlanetActive(it.javaClass) }
        .filterIsInstance<T>()
        .toList()

    private fun hasDirectCopy(planet: PlanetClass): Boolean =
        playerData.gameClasses.any { it !== this && planet.javaClass.isInstance(it) }

    private fun createBodies() {
        val definitions = listOf(
            BodyDefinition(sol, Material.MAGMA_BLOCK, 0.0, 1.25f, 0.0, Color.fromRGB(255, 145, 28)),
            BodyDefinition(mercurius, Material.REDSTONE_BLOCK, 2.1, 0.28f, 0.055, Color.fromRGB(150, 145, 140)),
            BodyDefinition(venus, Material.YELLOW_TERRACOTTA, 2.8, 0.48f, 0.043, Color.fromRGB(226, 181, 76)),
            BodyDefinition(terra, Material.GRASS_BLOCK, 3.6, 0.52f, 0.036, Color.fromRGB(62, 150, 235)),
            BodyDefinition(luna, Material.LIGHT_GRAY_CONCRETE, 0.72, 0.22f, 0.095, Color.fromRGB(220, 224, 232), true),
            BodyDefinition(mars, Material.RED_SANDSTONE, 4.45, 0.38f, 0.029, Color.fromRGB(205, 75, 43)),
            BodyDefinition(jupiter, Material.SCULK, 5.5, 0.95f, 0.017, Color.fromRGB(205, 164, 124)),
            BodyDefinition(saturnus, Material.SANDSTONE, 6.7, 0.82f, 0.013, Color.fromRGB(227, 203, 139)),
            BodyDefinition(uranus, Material.PACKED_ICE, 7.8, 0.66f, 0.0095, Color.fromRGB(125, 226, 235)),
            BodyDefinition(neptune, Material.LAPIS_BLOCK, 8.8, 0.64f, 0.0075, Color.fromRGB(52, 85, 220)),
            BodyDefinition(pluto, Material.ORANGE_CONCRETE_POWDER, 9.7, 0.20f, 0.0055, Color.fromRGB(180, 130, 95)),
        )
        definitions.forEachIndexed { index, definition ->
            val body = OrbitBody(definition, index * 2.0 * PI / definitions.size)
            bodies += body
            bodyRegistrations += AttackableObjectManager.register(
                ownerId = player.uniqueId,
                world = player.world,
                canBeHitBy = ::canBodyBeHitBy,
                hitboxes = { bodyHitboxes(body) },
                onHit = {
                    body.display?.takeIf { it.isValid }?.location?.clone()?.let { location ->
                        destroyPlanet(body, location, currentTick())
                    }
                },
            )
        }
    }

    private fun canBodyBeHitBy(attackerId: java.util.UUID?): Boolean {
        if (attackerId == null) return true
        if (attackerId == player.uniqueId) return false
        val attackerData = game.playerDatas.filterIsInstance<PlayerData>()
            .firstOrNull { it.uniqueId == attackerId }
        return attackerData?.isEnemyOf(playerData) ?: true
    }

    private fun bodyHitboxes(body: OrbitBody): List<BoundingBox> {
        val display = body.display?.takeIf { it.isValid } ?: return emptyList()
        val location = display.location
        val halfSize = body.definition.size / 2.0 + 0.12
        return listOf(BoundingBox(
            location.x - halfSize,
            location.y - halfSize,
            location.z - halfSize,
            location.x + halfSize,
            location.y + halfSize,
            location.z + halfSize,
        ))
    }

    private fun startOrbitTask() {
        orbitTask = playerData.trackTask(object : BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    clearBodies()
                    cancel()
                    return
                }
                if (game.isPaused) return
                val now = currentTick()
                var earthLocation: Location? = null
                bodies.forEach { body ->
                    val planet = body.definition.planet
                    val regenerationTick = destroyedUntil[planet.javaClass] ?: 0L
                    if (regenerationTick > now) {
                        body.display?.remove()
                        body.display = null
                        return@forEach
                    }
                    if (regenerationTick in 1L..now) {
                        destroyedUntil.remove(planet.javaClass)
                        onPlanetRegenerated(planet)
                    }
                    val location = calculateLocation(body, tick, earthLocation)
                    if (planet === terra) earthLocation = location.clone()
                    val display = body.display?.takeIf { it.isValid }
                        ?: spawnBody(body, location).also { body.display = it }
                    display.teleport(location)
                    if (tick % 4 == 0) renderTrail(body, location)
                    if (tick % 2 == 0) checkCollision(body, location, now)
                }
                tick++
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 1L))
    }

    private fun calculateLocation(body: OrbitBody, tick: Int, earth: Location?): Location {
        val definition = body.definition
        if (definition.planet === sol) {
            return player.location.clone().add(0.0, player.height + 0.65, 0.0).fixedDisplayRotation()
        }
        val origin = if (definition.moon) earth ?: player.location else player.location
        val angle = body.startAngle + tick * definition.speed
        return origin.clone().add(
            cos(angle) * definition.radius,
            1.0 + sin(angle * 0.73 + body.startAngle) * definition.radius * 0.09,
            sin(angle) * definition.radius,
        ).fixedDisplayRotation()
    }

    private fun spawnBody(body: OrbitBody, location: Location): BlockDisplay =
        location.world.spawn(location, BlockDisplay::class.java).apply {
            block = body.definition.material.createBlockData()
            val size = body.definition.size
            transformation = Transformation(
                Vector3f(-size / 2f, -size / 2f, -size / 2f), Quaternionf(),
                Vector3f(size, size, size), Quaternionf(),
            )
            brightness = Display.Brightness(15, 15)
            interpolationDuration = 1
            teleportDuration = 1
            isPersistent = false
            TemporaryDisplayManager.mark(this, player.uniqueId)
        }

    private fun renderTrail(body: OrbitBody, location: Location) {
        val dust = Particle.DustOptions(body.definition.color, (body.definition.size * 0.9f).coerceAtLeast(0.55f))
        particles.spawn(location, Particle.DUST, dust, ParticleOptions(force = true))
        if (body.definition.planet === sol) particles.spawn(location, Particle.FLAME, count = 5, spread = 0.65, speed = 0.025)
    }

    private fun checkCollision(body: OrbitBody, location: Location, now: Long) {
        val definition = body.definition
        val radius = definition.size * 0.55 + 0.28
        val target = playerData.radius(
            location,
            TargetType.Enemy,
            radius,
            false,
            hitAttackableObjects = false,
        )
            .firstOrNull { HitboxUtil.intersectsSphere(it.entity.boundingBox, location.toVector(), radius) }
            ?: return
        target.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        destroyPlanet(body, location, now)
    }

    private fun destroyPlanet(body: OrbitBody, location: Location, now: Long) {
        val definition = body.definition
        if ((destroyedUntil[definition.planet.javaClass] ?: 0L) > now) return
        destroyedUntil[definition.planet.javaClass] = now + 400L
        body.display?.remove()
        body.display = null
        removePlanetItems(definition.planet)
        particles.spawn(location, Particle.EXPLOSION, count = 1)
        particles.spawn(location, Particle.BLOCK, definition.material.createBlockData(), ParticleOptions.spread(25, 0.45, 0.11))
        sounds.play(location, Sound.ENTITY_GENERIC_EXPLODE, volume = 0.55f, pitch = 1.45f)
        playerData.updateStatusActionBar()
    }

    private fun onPlanetRegenerated(planet: PlanetClass) {
        ensurePlanetItems(planet)
        particles.spawn(player.location.clone().add(0.0, 1.0, 0.0), Particle.TOTEM_OF_UNDYING, count = 20, spread = 0.65, speed = 0.08)
        sounds.play(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume = 0.55f, pitch = 1.6f)
        player.sendMiniMessage("<green><bold>${UtilManager.applyKeywords(planet.name)} 재생</bold>")
        playerData.updateStatusActionBar()
    }

    private fun ensureUranusEquipment() {
        ensurePlanetItems(uranus)
        if (player.inventory.contents.none { it?.type == Material.ARROW }) player.inventory.addItem(ItemStack(Material.ARROW, 48))
    }

    private fun ensurePlanetItems(planet: PlanetClass) {
        if (planet === uranus && player.inventory.contents.none {
                it != null && getWeaponClassId(it) == Uranus::class.java.name
            }) giveOrDrop(uranus.toWeaponItemStack(player))

        planet.skills.forEach { skill ->
            if (player.inventory.contents.none { it != null && getSkillId(it, player.uniqueId) == skill.id }) {
                val index = skills.indexOf(skill).coerceAtLeast(0)
                val display = ItemStack(skillDyeMaterial(index)).apply {
                    itemMeta = itemMeta.apply {
                        displayName(MiniMessage.miniMessage().deserialize(UtilManager.applyKeywords(skill.name)))
                    }
                }
                giveOrDrop(markSkillItem(
                    ItemDescriptionManager.applyForPlayer(
                        display,
                        player,
                        skill.description,
                        skill.briefDescription,
                        ItemDescriptionManager.cooldownLines(skill.cooldown),
                    ),
                    skill, player.uniqueId
                ))
            }
        }
    }

    private fun removePlanetItems(planet: PlanetClass) {
        if (planet === uranus && !hasDirectCopy(planet)) {
            player.inventory.contents.forEachIndexed { slot, item ->
                if (item != null && getWeaponClassId(item) == Uranus::class.java.name) player.inventory.setItem(slot, null)
            }
        }
        val ids = planet.skills.map { it.id }.toSet()
        player.inventory.contents.forEachIndexed { slot, item ->
            if (item != null && getSkillId(item, player.uniqueId) in ids) player.inventory.setItem(slot, null)
        }
    }

    private fun giveOrDrop(item: ItemStack) {
        player.inventory.addItem(item).values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover).apply {
                owner = player.uniqueId
                pickupDelay = 0
            }
        }
    }

    private fun createPlanetStatuses() {
        clearPlanetStatuses()
        planets.forEach { planet ->
            val plainName = planet.name.replace(Regex("<[^>]+>"), "")
            val status = SolarPlanetStatus(plainName) {
                val remainingTicks = (destroyedUntil[planet.javaClass] ?: 0L) - currentTick()
                if (remainingTicks > 0L) ceil(remainingTicks / 20.0).toInt() else null
            }
            playerData.addStatus(status, playerData)
            planetStatuses += status
        }
    }

    private fun clearPlanetStatuses() {
        planetStatuses.toList().forEach { status ->
            if (status in playerData.statusAbnormalitys) status.remove()
        }
        planetStatuses.clear()
    }

    private fun Location.fixedDisplayRotation(): Location = apply {
        yaw = 0.0f
        pitch = 0.0f
    }

    private fun currentTick() = Bukkit.getCurrentTick().toLong()

    private fun clearBodies() {
        orbitTask?.cancel()
        orbitTask = null
        bodyRegistrations.forEach(AttackableObjectManager.Registration::unregister)
        bodyRegistrations.clear()
        bodies.forEach { it.display?.remove() }
        bodies.clear()
    }

    private data class BodyDefinition(
        val planet: PlanetClass, val material: Material, val radius: Double,
        val size: Float, val speed: Double, val color: Color, val moon: Boolean = false,
    )
    private data class OrbitBody(
        val definition: BodyDefinition, val startAngle: Double, var display: BlockDisplay? = null,
    )

    private class SolarPlanetStatus(
        private val planetName: String,
        private val remainingRegenerationSeconds: () -> Int?,
    ) : StatusAbnormality() {
        override val name = "<gray><bold>$planetName</bold></gray>"
        override val description = listOf("<gray>태양계에서 공전 중인 ${planetName}의 상태이다.")
        override val canRemove = true
        override var power = 1
        override var maxPower: Int? = 1
        override val showPower = false
        override val showMaxPower = false
        override val isClassMechanic = true
        override var duration: Int? = null

        override fun actionBarText(): String {
            val remaining = remainingRegenerationSeconds()
            return if (remaining == null) {
                "$name <yellow>∞</yellow>"
            } else {
                "<dark_gray><bold>$planetName</bold></dark_gray> <yellow>${remaining}s</yellow>"
            }
        }
    }

    private class Passive : BasePassive() {
        override val name = "<bold>공전 궤도"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>현실의 크기와 순서를 반영한 태양계 천체들이 서로 다른 속도로 자신 주위를 공전한다.",
            "<gray>공전하는 천체에 충돌한 적은 2의 피해를 입고, 20초간 해당 천체가 파괴된다.",
            "<gray>천체는 적의 기본 공격 또는 투사체에 적중해도 파괴된다.", "",
            "<gray>공전 중인 천체 클래스의 능력, 패시브, 무기를 모두 얻는다.",
            "<gray>파괴된 천체의 능력, 패시브, 무기는 재생될 때까지 제거된다."
        )
    }
}
