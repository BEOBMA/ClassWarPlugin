package org.beobma.classWarPlugin.gameClass.constellations

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.*
import org.beobma.classWarPlugin.domain.*
import org.beobma.classWarPlugin.effect.ParticleApi
import org.beobma.classWarPlugin.effect.SoundApi
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.creator.CreationGeometry
import org.beobma.classWarPlugin.manager.*
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.SkillManager.shotLaserGetEntityData
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

/** Eight numbered stars may occupy any distinct cells of the six-row inventory. */
internal class StarSequence(val slots: List<Int>) {
    init { require(slots.size == 8 && slots.distinct().size == 8 && slots.all { it in 0 until 54 }) }
    var completed = 0; private set
    var failed = false; private set
    val count get() = completed * (completed + 1) / 2
    fun choose(slot: Int): Boolean {
        if (failed || completed >= 8) return false
        if (slot != slots[completed]) { failed = true; return false }
        completed++
        return true
    }
}

class ConstellationRuntime(private val scope: AbilityScope) : Listener {
    private val owner get() = scope.playerData
    private val player get() = owner.player
    private var clock = 0L
    private var attacks = 0
    private var domain: DomainSession? = null
    val inDomain get() = domain != null
    private var suppressedUntil = 0L
    private var pending: Pair<UUID, () -> Unit>? = null
    private val ownedStatuses = mutableSetOf<StatusAbnormality>()
    private data class Fire(val entity: LivingEntity, val oldUntil: Long, var oursUntil: Long)
    private val fires = mutableMapOf<UUID, Fire>()
    private data class Challenge(val inventory: Inventory, val order: StarSequence,
        val end: Long, val statuses: List<StatusAbnormality>, var finishing: Boolean = false)
    private var challenge: Challenge? = null
    private val puzzleKey get() = NamespacedKey(ClassWarPlugin.instance, "constellation-puzzle")
    private data class Star(val display: ItemDisplay, var at: Location, var velocity: Vector,
        var target: EntityData?, val guidance: Double, var age: Int = 0, var missed: Boolean = false,
        var registration: AttackableObjectManager.Registration? = null)
    private data class Orbit(val target: EntityData, var until: Long, val stars: MutableMap<Class<*>, ItemDisplay> = linkedMapOf(),
        val registrations: MutableMap<Class<*>, AttackableObjectManager.Registration> = linkedMapOf(),
        var ringStarted: Long? = null)
    private val stars = mutableListOf<Star>()
    private data class StarVolley(val origin: Location, val count: Int, var emitted: Int = 0)
    private val volleys = ArrayDeque<StarVolley>()
    private val orbits = mutableMapOf<UUID, Orbit>()

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, ClassWarPlugin.instance)
        scope.resources.own { HandlerList.unregisterAll(this); cancelChallenge(); clearStars(); clearStatuses() }
        object : AbilityRunnable(scope) {
            override fun run() {
                clock++
                if (challenge?.let { clock >= it.end } == true) finishChallenge(true)
                tickVolley()
                stars.toList().forEach(::tickStar)
                orbits.values.toList().forEach(::tickOrbit)
                ownedStatuses.removeIf { it.power <= 0 }
            }
            override fun onCancel() { cancelChallenge(); clearStars() }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    fun confirmed(context: DamageContext) {
        val callback = pending
        if (callback != null && callback.first == context.target.entity.uniqueId) {
            pending = null; callback.second(); return
        }
        if (context.path.isBasicAttack && !context.secondaryAttack && ++attacks % 2 == 0)
            summon(context.target.entity.location, context.target, weak = false)
    }

    private fun enemies() = Targeting.select(owner, TargetType.Enemy, player.world)
    private fun valid(target: EntityData) = target.entity.isValid && !target.entity.isDead &&
        target.entity.world == player.world && Targeting.isEnemy(owner, target)

    fun applied(target: EntityData, status: StatusAbnormality) {
        if (target === owner || !valid(target) || status !is Bleeding && status !is Burn && status !is Brightness && status !is Frostbite) return
        ownedStatuses += status
        if (clock < suppressedUntil) return
        val orbit = orbits.getOrPut(target.entity.uniqueId) { Orbit(target, clock + 200) }
        orbit.until = clock + 200
        if (status.javaClass !in orbit.stars) {
            val display = display(target.entity.boundingBox.center.toLocation(target.entity.world), 0.35f)
            orbit.stars[status.javaClass] = display
            if (inDomain) registerOrbit(orbit, status.javaClass, display)
        }
    }

    private fun randomStatus(target: EntityData) {
        val factory: () -> StatusAbnormality = when (Random.nextInt(4)) {
            0 -> ::Bleeding; 1 -> ::Burn; 2 -> ::Brightness; else -> ::Frostbite
        }
        val candidate = factory()
        val status = target.statusAbnormalitys.firstOrNull {
            it.javaClass == candidate.javaClass && it.balanceCasterData() === owner && it.effectSource === scope
        } ?: target.addStatus(candidate, owner)
        if (status.applicationBlocked) return
        if (status is Burn) {
            (target.entity as? LivingEntity)?.let { living ->
                val fire = fires.getOrPut(living.uniqueId) { Fire(living, clock + living.fireTicks.coerceAtLeast(0), clock + 200) }
                fire.oursUntil = clock + 200
            }
        }
        status.updatePower(minOf(4, status.power + 1, status.maxPower ?: 4))
        status.updateDuration(10)
        applied(target, status)
    }

    fun guidance(): Boolean {
        if (challenge != null) return false
        player.closeInventory()
        val effects = listOf(Disarm(), Silence(), WhenDamageReduction()).map {
            owner.addStatus(it, owner).also { status -> status.updatePower(if (status is WhenDamageReduction) 50 else 1); status.updateDuration(5) }
        }
        val inventory = Bukkit.createInventory(null, 54,
            UtilManager.miniMessage.deserialize("<dark_purple>별의 인도 <gray>· 1 → 8 · 5초"))
        val order = StarSequence((0 until 54).shuffled().take(8))
        challenge = Challenge(inventory, order, clock + 100, effects)
        order.slots.forEachIndexed { index, slot ->
            inventory.setItem(slot, ItemStack(Material.NETHER_STAR, index+1).apply {
                itemMeta = itemMeta.apply {
                    displayName(UtilManager.miniMessage.deserialize("<gold><bold>${index+1}번째 별</bold></gold>"))
                    persistentDataContainer.set(puzzleKey, PersistentDataType.BYTE, 1)
                }
            })
        }
        if (player.openInventory(inventory) == null) { cancelChallenge(); return false }
        SoundApi.play(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.4f)
        return true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun click(event: InventoryClickEvent) {
        if (event.whoClicked.uniqueId != owner.uniqueId) return
        val puzzle = challenge ?: return
        event.isCancelled = true
        if (scope.game.isPaused || scope.suspended || puzzle.finishing) return
        if (event.view.topInventory !== puzzle.inventory || event.rawSlot !in 0 until 54) return
        if (event.click != ClickType.LEFT && event.click != ClickType.RIGHT) return
        if (event.rawSlot !in puzzle.order.slots || puzzle.inventory.getItem(event.rawSlot) == null) return
        AbilityExecution.with(scope) {
            if (!puzzle.order.choose(event.rawSlot)) {
                queueFinish(puzzle); return@with
            }
            puzzle.inventory.setItem(event.rawSlot, null)
            SoundApi.playTo(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, (0.7 + puzzle.order.completed * 0.15).toFloat())
            ParticleApi.spawn(player.eyeLocation, Particle.END_ROD, 5, 0.3, 0.02)
            if (puzzle.order.completed == 8) queueFinish(puzzle)
        }
    }
    // Closing an inventory inside InventoryClickEvent is unsafe; settle on the next server tick.
    private fun queueFinish(puzzle: Challenge) {
        if (puzzle.finishing) return
        puzzle.finishing = true
        object : AbilityRunnable(scope) {
            override fun run() { if (challenge === puzzle) finishChallenge(true) }
        }.runTaskLater(ClassWarPlugin.instance, 1)
    }
    @EventHandler fun close(event: InventoryCloseEvent) {
        val puzzle = challenge ?: return
        if (event.player.uniqueId == owner.uniqueId && event.inventory === puzzle.inventory) queueFinish(puzzle)
    }
    @EventHandler fun drag(event: InventoryDragEvent) { if (challenge != null && event.whoClicked.uniqueId == owner.uniqueId) event.isCancelled = true }
    @EventHandler fun drop(event: PlayerDropItemEvent) { if (challenge != null && event.player.uniqueId == owner.uniqueId) event.isCancelled = true }
    @EventHandler fun pickup(event: EntityPickupItemEvent) { if (challenge != null && event.entity.uniqueId == owner.uniqueId) event.isCancelled = true }
    @EventHandler fun swap(event: PlayerSwapHandItemsEvent) { if (challenge != null && event.player.uniqueId == owner.uniqueId) event.isCancelled = true }
    @EventHandler fun quit(event: PlayerQuitEvent) { if (event.player.uniqueId == owner.uniqueId) cancelChallenge() }
    @EventHandler(priority = EventPriority.LOWEST) fun death(event: PlayerDeathEvent) {
        if (event.player.uniqueId != owner.uniqueId) return
        cancelChallenge()
    }

    fun cancelChallenge() = finishChallenge(false)
    private fun finishChallenge(fire: Boolean) {
        val puzzle = challenge ?: return
        challenge = null
        puzzle.inventory.clear()
        if (player.openInventory.topInventory === puzzle.inventory) player.closeInventory()
        puzzle.statuses.forEach { it.remove() }
        if (!fire || !player.isOnline || owner.entityStatus.isDead || scope.isClosed) return
        if (puzzle.order.count > 0) volleys.addLast(StarVolley(player.location.clone(), puzzle.order.count))
        SoundApi.play(player.location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.7f)
    }

    /** One star per combat tick (50 ms at 20 TPS), including overlapping guidance casts. */
    private fun tickVolley() {
        while (volleys.isNotEmpty()) {
            val volley = volleys.first()
            if (volley.origin.world != player.world) { volleys.removeFirst(); continue }
            val index = volley.emitted++
            val angle = 2 * PI * index / volley.count
            val point = volley.origin.clone().add(cos(angle) * (4 + index%3), 0.0, sin(angle) * (4 + index%3))
            if (!inDomain && index % 4 == 0) {
                val sky = volley.origin.clone().apply { y = minOf(y+14.0, world.maxHeight-1.0) }
                repeat(48) { step ->
                    val a = step*2*PI/48
                    ParticleApi.spawn(sky.clone().add(cos(a)*6,0.0,sin(a)*6),Particle.END_ROD)
                }
                repeat(6) { arm ->
                    val a = arm*PI/3
                    val b = a+2*PI/3
                    ParticleApi.line(sky.clone().add(cos(a)*6,0.0,sin(a)*6),
                        sky.clone().add(cos(b)*6,0.0,sin(b)*6),Particle.ENCHANT,spacing=0.7)
                }
            }
            // Re-evaluate each shot: a target may have died or moved since the puzzle ended.
            val target = enemies().filter { valid(it) && it.entity.location.distanceSquared(volley.origin) <= 18*18 }
                .minByOrNull { it.entity.location.distanceSquared(point) }
            summon(point, target, weak = true)
            SoundApi.play(volley.origin, Sound.BLOCK_NOTE_BLOCK_HAT, 0.22f, (1.3 + index%4*0.12).toFloat())
            if (volley.emitted >= volley.count) volleys.removeFirst()
            return
        }
    }

    private fun display(at: Location, scale: Float): ItemDisplay = at.world.spawn(at, ItemDisplay::class.java).also {
        it.setItemStack(ItemStack(Material.NETHER_STAR))
        it.billboard = Display.Billboard.CENTER
        it.brightness = Display.Brightness(15,15)
        it.teleportDuration = 1
        it.transformation = it.transformation.apply { this.scale.set(scale) }
        TemporaryDisplayManager.mark(it, owner.uniqueId)
    }
    private fun randomDirection(): Vector {
        val y = Random.nextDouble(-1.0, 1.0); val a = Random.nextDouble(2*PI); val r = sqrt(1-y*y)
        return Vector(cos(a)*r, y, sin(a)*r)
    }
    private fun summon(point: Location, target: EntityData?, weak: Boolean) {
        val area = domain
        val chosen = if (area != null) target?.takeIf { area.contains(it.entity.location) }
            ?: enemies().filter { area.contains(it.entity.location) }.minByOrNull { it.entity.location.distanceSquared(point) } else target
        if (area != null && chosen == null) return
        val from = if (area != null) area.center.clone().add(randomDirection().multiply(20.0))
            else point.clone().add(if (weak) 0.0 else Random.nextDouble(-4.0,4.0), 14.0,
                if (weak) 0.0 else Random.nextDouble(-4.0,4.0)).apply {
                y = minOf(y, world.maxHeight - 1.0)
            }
        val goal = chosen?.entity?.boundingBox?.center ?: point.toVector()
        val delta = goal.subtract(from.toVector())
        val velocity = if (area == null || delta.lengthSquared() < 1e-8) Vector(0.0,-1.0,0.0)
            else delta.normalize()
        val star = Star(display(from,0.55f), from, velocity, chosen, if (weak) StarSteering.GUIDANCE_TURN else 0.055)
        stars += star
        if (area != null) registerStar(star)
        ParticleApi.spawn(from, Particle.FIREWORK, 3,0.15,0.01)
    }

    private fun registerStar(star: Star) {
        if (star.registration != null) return
        star.registration = AttackableObjectManager.register(owner.uniqueId, star.at.world, acceptsAreaSkills = true,
            canBeHitBy = { id -> enemies().any { it.entity.uniqueId == id } },
            hitboxes = { listOf(BoundingBox.of(star.at,0.4,0.4,0.4)) }, onHit = { destroy(star, true) })
    }
    private fun tickStar(star: Star) {
        if (star !in stars) return
        if ((++star.age > 160 && !inDomain) || !star.display.isValid || star.at.world != player.world) { destroy(star); return }
        val area = domain
        if (area != null && star.target?.let { valid(it) && area.contains(it.entity.location) } != true) {
            star.target = enemies().firstOrNull { area.contains(it.entity.location) }
            if (star.target == null) { destroy(star); return }
        }
        val target = star.target?.takeIf(::valid)
        if (target != null) {
            val desired = target.entity.boundingBox.center.subtract(star.at.toVector())
            // Once an exterior star has passed its target, it continues forward without reacquisition.
            if (area == null && desired.dot(star.velocity) <= 0) star.missed = true
            if (area != null || !star.missed)
                star.velocity = StarSteering.turn(star.velocity, desired,
                    if (area != null) 0.16 else StarSteering.exteriorTurn(star.guidance, desired.length()))
        }
        if (star.velocity.lengthSquared() < 1e-8) star.velocity = Vector(0.0,-1.0,0.0)
        val direction = star.velocity.clone().normalize()
        val length = if (area != null && target != null) {
            val offset = target.entity.boundingBox.center.subtract(star.at.toVector())
            val alignment = if (offset.lengthSquared() > 1e-8) direction.dot(offset.clone().normalize()) else 1.0
            // Brake through wide return turns; speed up once aligned, preventing endless tight orbits.
            val speed = if (alignment < 0.95) 0.35 else maxOf(1.25, target.entity.velocity.length()+0.35)
            minOf(offset.length().coerceAtLeast(0.05), speed)
        } else minOf(1.25, 0.7 + star.age*0.012)
        val wall = if (area == null) star.at.world.rayTraceBlocks(star.at,direction,length)?.hitPosition else null
        val distance = wall?.distance(star.at.toVector()) ?: length
        val hit = enemies().mapNotNull { enemy ->
            CreationGeometry.contact(enemy.entity.boundingBox,star.at.toVector(),direction,distance,0.16)
                ?.let { Triple(enemy,it,it.distance(star.at.toVector())) }
        }.minByOrNull { it.third }
        val next = hit?.second?.toLocation(star.at.world) ?: star.at.clone().add(direction.multiply(distance))
        ParticleApi.line(star.at,next,Particle.END_ROD,spacing=0.35)
        star.at = next
        star.display.teleport(next)
        star.display.transformation = star.display.transformation.apply { leftRotation.rotationZ(star.age*0.18f) }
        if (hit != null) {
            val previous = pending
            pending = hit.first.entity.uniqueId to { randomStatus(hit.first) }
            try { hit.first.damage(0.1,DamageType.True,owner,damagePath=DamagePath.SKILL,secondaryAttack=true) }
            finally { pending = previous }
            SoundApi.play(next,Sound.BLOCK_AMETHYST_BLOCK_HIT,0.3f,1.6f)
            destroy(star,true)
        } else if (wall != null) destroy(star,true)
    }
    private fun destroy(star: Star, effect: Boolean = false) {
        if (!stars.remove(star)) return
        star.registration?.unregister(); star.display.remove()
        if (effect) ParticleApi.spawn(star.at,Particle.FIREWORK,8,0.25,0.03)
    }

    private fun registerOrbit(orbit: Orbit, type: Class<*>, display: ItemDisplay) {
        orbit.registrations.getOrPut(type) {
            AttackableObjectManager.register(owner.uniqueId,display.world,acceptsAreaSkills=true,
                canBeHitBy = { id -> enemies().any { it.entity.uniqueId == id } },
                hitboxes = { listOf(BoundingBox.of(display.location,0.35,0.35,0.35)) }, onHit = {
                    orbit.stars.remove(type)?.remove()
                    orbit.registrations.remove(type)?.unregister()
                })
        }
    }
    private fun tickOrbit(orbit: Orbit) {
        if (!valid(orbit.target) || clock >= orbit.until) { removeOrbit(orbit); return }
        val elapsed = orbit.ringStarted?.let { clock-it }
        if (elapsed != null && elapsed >= 60) {
            val canSettle = orbit.stars.size == 4
            val target = orbit.target
            val at = target.entity.boundingBox.center.toLocation(target.entity.world)
            removeOrbit(orbit)
            if (canSettle) {
                target.addStatus(Settlement(),owner).updatePower(1)
                ParticleApi.spawn(at,Particle.FLASH,1)
                ParticleApi.spawn(at,Particle.FIREWORK,32,0.8,0.08)
                SoundApi.play(at,Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,0.7f,1.1f)
            }
            return
        }
        val center = orbit.target.entity.boundingBox.center.toLocation(orbit.target.entity.world)
        val phase = if (elapsed == null) clock*0.06 else elapsed*0.08 + elapsed*elapsed*0.009
        val radius = if (elapsed == null) 1.2 else 1.2*(1-elapsed/70.0)
        orbit.stars.values.toList().forEachIndexed { index, display ->
            val angle = phase + index*2*PI/4
            display.teleport(center.clone().add(cos(angle)*radius, sin(angle*2)*0.3, sin(angle)*radius))
        }
        if (elapsed != null && clock%2 == 0L) {
            repeat(18) { i ->
                val angle = phase + i*2*PI/18
                ParticleApi.spawn(center.clone().add(cos(angle)*radius,0.0,sin(angle)*radius),Particle.END_ROD)
            }
            if (elapsed%10 == 0L) SoundApi.play(center,Sound.BLOCK_NOTE_BLOCK_CHIME,0.4f,(0.6+elapsed/50.0).toFloat())
        }
    }
    private fun removeOrbit(orbit: Orbit) {
        orbits.remove(orbit.target.entity.uniqueId)
        orbit.stars.values.forEach { it.remove() }; orbit.registrations.values.forEach { it.unregister() }
        orbit.stars.clear(); orbit.registrations.clear()
    }
    fun ring(): Boolean {
        val target = owner.shotLaserGetEntityData(10.0,TargetType.Enemy,false)
        val orbit = target?.let { orbits[it.entity.uniqueId] }
        if (orbit == null || orbit.stars.size != 4 || orbit.ringStarted != null) {
            player.sendMessage("§c10칸 내에서 별 4개가 공전 중인 적을 바라보아야 한다."); return false
        }
        orbit.ringStarted = clock; orbit.until = maxOf(orbit.until, clock+61)
        SoundApi.play(target.entity,Sound.BLOCK_BEACON_ACTIVATE,0.5f,1.3f)
        return true
    }

    private fun clearStars() { volleys.clear(); stars.toList().forEach { destroy(it) }; orbits.values.toList().forEach(::removeOrbit) }
    private fun clearStatuses() {
        ownedStatuses.toList().forEach { it.remove() }; ownedStatuses.clear()
        fires.values.forEach { fire ->
            if (fire.entity.isValid && fire.entity.fireTicks <= (fire.oursUntil-clock).coerceAtLeast(0)+2)
                fire.entity.fireTicks = (fire.oldUntil-clock).coerceAtLeast(0).toInt()
        }
        fires.clear()
    }
    private fun basicSkills() = scope.owner.skills.filter { it.definitionId != "constellations/domain" }
    fun expand(): Boolean = DomainManager.expand(scope, DomainDefinition(
        name="별이 빛나는 밤",radius=25,durationTicks=320,floor=Material.BARRIER,
        interiorLightLevel=15,
        presentation={ ConstellationPresentation(it) },
        onStart={ session ->
            domain=session
            basicSkills().forEach { CooldownManager.resetCooldown(player,it) }
            stars.forEach(::registerStar)
            orbits.values.forEach { orbit -> orbit.stars.forEach { (type,display) -> registerOrbit(orbit,type,display) } }
        },
        onTick={ basicSkills().forEach { CooldownManager.resetCooldown(player,it) } },
        onEnd={ domain=null; cancelChallenge(); clearStars(); clearStatuses(); suppressedUntil=clock+400 },
    ))
}
