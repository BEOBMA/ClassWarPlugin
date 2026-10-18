package org.beobma.classWarPlugin.gameClass.art

import io.papermc.paper.datacomponent.item.ResolvableProfile
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.EffectApiAccess
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.*
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Distortion
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Mannequin
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID

internal object PlaybackRules {
    fun indices(last: Int, forward: Boolean): List<Int> = if (forward) (0..last).toList() else (last downTo 0).toList()
    fun cloneDue(tick: Int, last: Int, interval: Int, acted: Boolean) = acted && tick - last >= interval
    fun restoreState(forward: Boolean, initial: Boolean = false) = !forward || initial
}

/** Recording reserves cooldowns without mutating the real cooldown or spending skill resources. */
internal class DeferredCastCooldowns {
    private val expires = mutableMapOf<String, Long>()
    fun ready(id: String, tick: Long): Boolean = tick >= (expires[id] ?: Long.MIN_VALUE)
    fun reserve(id: String, tick: Long, ticks: Long) { expires[id] = tick + ticks.coerceAtLeast(1L) }
}

/** The timeline records values, never health or inventory contents (no healing/item duplication). */
class CartoonRuntime(private val scope: AbilityScope) : EffectApiAccess {
    private val owner get() = scope.playerData
    private val game get() = scope.game
    private var session: Session? = null

    fun canStart(): Boolean {
        if (session == null && sessions.none { owner.uniqueId in it.tracks }) return true
        owner.player.sendMiniMessage("<red><bold>[!] 이미 기록 또는 재생 중입니다.")
        return false
    }

    fun start(forward: Boolean): Boolean {
        if (!canStart()) return false
        val participants = if (forward) listOf(owner) else
            (listOf(owner) + owner.radius(owner.player.location, TargetType.Enemy, 6.0, false).filterIsInstance<PlayerData>()).distinct()
        if (participants.any { data -> sessions.any { data.uniqueId in it.tracks } }) {
            owner.player.sendMiniMessage("<red><bold>[!] 대상이 이미 다른 시간 기록에 묶여 있습니다.")
            return false
        }
        val created = Session(forward, participants)
        session = created
        sessions += created
        scope.resources.own(isAlive = { !created.closed }) { created.close() }
        try { created.start() } catch (error: Throwable) { created.close(); throw error }
        return true
    }

    private data class Cooldown(val skill: Skill, val remaining: Int, val material: Material)
    private data class Resource(val status: StatusAbnormality, val power: Int, val duration: Int?)
    private data class Frame(val at: Location, val velocity: Vector, val sneak: Boolean, val sprint: Boolean,
                             val heldSlot: Int, val cooldowns: List<Cooldown>, val resources: List<Resource>,
                             val effects: Map<AbilityScope, Long>)
    private sealed interface Action {
        data class Cast(val skill: Skill, val item: ItemStack, val at: Location) : Action
        data class Attack(val target: EntityData, val damage: Double, val type: DamageType, val path: DamagePath,
                          val at: Location, val range: Double, val attackEffectsAllowed: Boolean) : Action
    }
    private class Track(val data: PlayerData) {
        val frames = mutableListOf<Frame>()
        val clones = mutableMapOf<Int, Mannequin>()
        val actions = mutableMapOf<Int, MutableList<Action>>()
        var lastClone = -10
        var changed = false
        var control: AutoCloseable? = null
        var hud: StatusAbnormality? = null
        var originalGravity = true
    }

    private inner class Session(val forward: Boolean, participants: List<PlayerData>) : AutoCloseable {
        val tracks = participants.associate { it.uniqueId to Track(it) }.toMutableMap()
        var closed = false
        var recording = true
        private var tick = 0
        private var position = 0
        private var order = emptyList<Int>()
        private var task: BukkitTask? = null
        private val distortion = ResourceScope()
        val reservations = DeferredCastCooldowns()

        fun start() {
            if (forward) owner.radius(owner.player.location, TargetType.Enemy, 16.0, false).forEach { target ->
                val id = target.entity.uniqueId
                distortionCounts[id] = (distortionCounts[id] ?: 0) + 1
                distortion.own { val count = (distortionCounts[id] ?: 1) - 1
                    if (count <= 0) distortionCounts.remove(id) else distortionCounts[id] = count }
                val status = target.addStatus(Distortion(), owner)
                distortion.own { status.cleanupFromManager() }
                if (target is PlayerData) {
                    val movement = target.attributeEffects.multiply(scope, Attribute.MOVEMENT_SPEED, 0.05)
                    val attack = target.attributeEffects.multiply(scope, Attribute.ATTACK_SPEED, 0.05)
                    distortion.own { movement.close(); attack.close() }
                }
            }
            tracks.values.forEach { track ->
                track.frames += capture(track.data)
                track.hud = track.data.addStatus(object : StatusAbnormality() {
                    override val name = if (forward) "<aqua><bold>배속 재생</bold>" else "<light_purple><bold>역재생</bold>"
                    override val description = listOf("<gray>기록한 행동을 순서대로 재생한다.")
                    override val canRemove = false
                    override val isClassMechanic = true
                    override var power = 1
                    override fun actionBarText(): String {
                        val remaining = if (recording) (60-tick)/20.0 else (order.size-position)/(if (forward) 40.0 else 20.0)
                        return "$name <white>${if (recording) "기록" else "재생"} <gray>${"%.1f".format(remaining.coerceAtLeast(0.0))}초"
                    }
                }, owner)
            }
            sounds.play(owner.player, Sound.ITEM_BOOK_PAGE_TURN, volume = 1.0f, pitch = if (forward) 1.7f else 0.65f)
            particles.spawn(owner.player, Particle.ENCHANT, count = 35, spread = 1.0, speed = 0.04)
            task = owner.trackTask(object : AbilityRunnable(scope, cancelOnDisconnect = true) {
                override fun onCancel() { close() }
                override fun run() {
                    if (closed) { cancel(); return }
                    if (tracks.values.any { !it.data.player.isOnline || it.data.entityStatus.isDead || it.data.player.world != it.frames.first().at.world }) {
                        close(); return
                    }
                    if (recording) record() else playback()
                }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L))
        }

        private fun record() {
            tick++
            tracks.values.forEach { track ->
                val frame = capture(track.data)
                val previous = track.frames.last()
                val acted = forward || track.changed || previous.at.distanceSquared(frame.at) > 0.0001 ||
                    previous.at.yaw != frame.at.yaw || previous.at.pitch != frame.at.pitch ||
                    previous.sneak != frame.sneak || previous.sprint != frame.sprint || previous.heldSlot != frame.heldSlot
                track.frames += frame
                if (PlaybackRules.cloneDue(tick, track.lastClone, if (forward) 5 else 10, acted)) {
                    track.lastClone = tick
                    track.clones[tick] = clone(track.data)
                    sounds.play(frame.at, Sound.ITEM_BOOK_PAGE_TURN, volume = 0.2f, pitch = 1.8f)
                }
                track.changed = false
            }
            if (tick < 60) return
            recording = false
            distortion.close()
            order = PlaybackRules.indices(tick, forward)
            tracks.values.forEach { track ->
                // Manual input is blocked by isLocked; don't block delayed damage from replayed skills.
                track.control = track.data.entityStatus.controlLocks.acquire(Control.MOVE)
                track.originalGravity = track.data.player.hasGravity()
                track.data.player.setGravity(false)
                if (forward) restore(track, track.frames.first(), rewindEffects = true, restoreState = true)
            }
            sounds.play(owner.player, Sound.BLOCK_PORTAL_TRAVEL, volume = 0.25f, pitch = if (forward) 1.8f else 0.6f)
        }

        private fun playback() {
            repeat(if (forward) 2 else 1) {
                if (closed) return
                if (position >= order.size) { close(); return }
                val index = order[position++]
                tracks.values.toList().forEach { track ->
                    if (closed || track.data.entityStatus.isDead || !track.data.player.isOnline) return@forEach
                    val frame = track.frames[index]
                    restore(track, frame, rewindEffects = !forward)
                    if (forward) track.actions[index].orEmpty().forEach { action -> perform(track, action) }
                    else if (track.actions[index].orEmpty().any { it is Action.Attack }) track.data.player.swingMainHand()
                    track.clones.remove(index)?.let { clone ->
                        clone.remove()
                        particles.spawn(frame.at.clone().add(0.0,1.0,0.0), Particle.REVERSE_PORTAL, count = 15, spread = 0.4, speed = 0.03)
                        sounds.play(frame.at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, volume = 0.3f, pitch = 1.6f)
                        if (!forward) track.data.damage(2.0, DamageType.Normal, owner, damagePath = DamagePath.STATUS_EFFECT)
                    }
                }
            }
        }

        private fun restore(track: Track, frame: Frame, rewindEffects: Boolean, restoreState: Boolean = PlaybackRules.restoreState(forward)) {
            val data = track.data
            if (data.entityStatus.isDead || !data.player.isOnline) return
            if (rewindEffects) frame.effects.forEach { (ability, checkpoint) ->
                if (!ability.isClosed) AbilityExecution.with(ability) { ability.resources.rewind(checkpoint) }
            }
            authorized(data.uniqueId) {
                val location = safeLocation(data, frame.at)
                if (location != null) data.player.teleport(location)
                data.player.velocity = Vector()
                data.player.fallDistance = 0f
                data.player.isSneaking = frame.sneak
                data.player.isSprinting = frame.sprint
                data.player.inventory.heldItemSlot = frame.heldSlot
                if (restoreState) frame.cooldowns.filter { !it.skill.abilityScope.isClosed }.forEach {
                    CooldownManager.restoreRemaining(data.player, it.skill, it.remaining, it.material)
                }
                // Restore live class resources, without resurrecting removed debuffs or health.
                if (restoreState) frame.resources.filter { it.status in data.statusAbnormalitys }.forEach {
                    it.status.fromSource {
                        it.status.updatePower(it.power)
                        it.status.duration = it.duration
                        it.status.onDurationChanged()
                    }
                }
            }
            particles.spawn(data.player.location.clone().add(0.0,1.0,0.0), Particle.ENCHANT, count = 3, spread = 0.3, speed = 0.01)
        }

        private fun perform(track: Track, action: Action) {
            val data = track.data
            if (data.entityStatus.isDead || !data.player.isOnline) return
            track.control?.close(); track.control = null
            try {
                authorized(data.uniqueId) {
                    when (action) {
                        is Action.Cast -> {
                            if (action.skill.abilityScope.isClosed || !action.skill.abilityScope.isActive) return@authorized
                            val origin = safeLocation(data, action.at) ?: return@authorized
                            if (!data.player.teleport(origin)) return@authorized
                            val previousCooldown = CooldownManager.remainingTicks(data.player, action.skill)
                            CooldownManager.resetCooldown(data.player, action.skill)
                            if (!data.use(action.skill, action.item.clone()))
                                CooldownManager.restoreRemaining(data.player, action.skill, previousCooldown, action.item.type)
                        }
                        is Action.Attack -> {
                            data.player.swingMainHand()
                            val target = action.target
                            if (target.entityStatus.isDead || !target.entity.isValid || target.entity.world != data.player.world) return@authorized
                            if (!HitboxUtil.intersectsSegment(target.entity.boundingBox, data.player.eyeLocation.toVector(),
                                    data.player.eyeLocation.toVector().add(action.at.direction.multiply(action.range)), 0.25)) return@authorized
                            // This is the first real hit, not a bonus/secondary hit: normal hit passives must run now.
                            org.beobma.classWarPlugin.damage.AttackEffectContext.with(data.uniqueId, action.attackEffectsAllowed) {
                                target.damage(action.damage, action.type, data, damagePath = action.path)
                            }
                        }
                    }
                }
            } finally {
                if (!closed && !data.entityStatus.isDead) track.control = data.entityStatus.controlLocks.acquire(Control.MOVE)
            }
        }

        fun addAction(data: PlayerData, action: Action) {
            if (!recording || closed) return
            val track = tracks[data.uniqueId] ?: return
            track.changed = true
            track.actions.getOrPut((tick + 1).coerceAtMost(60)) { mutableListOf() }.add(action)
        }

        override fun close() {
            if (closed) return
            closed = true
            sessions -= this
            task?.cancel()
            distortion.close()
            tracks.values.forEach { track ->
                track.control?.close(); track.control = null
                track.hud?.cleanupFromManager(); track.hud = null
                if (!recording) {
                    track.data.player.setGravity(track.originalGravity)
                    track.data.player.velocity = Vector()
                    track.data.player.fallDistance = 0f
                }
                track.clones.values.forEach { it.remove() }; track.clones.clear()
            }
            session = null
        }
    }

    private fun capture(data: PlayerData): Frame {
        val player = data.player
        val abilities = AbilityTree.nodes(data.gameClasses, activeOnly = true).filter { it.classId != "cartoonist" }
        val cooldowns = abilities.flatMap { it.skills }.map { skill ->
            val item = player.inventory.contents.filterNotNull().filterNot { it.type.isAir }
                .firstOrNull { SkillManager.getSkillId(it, data.uniqueId) == skill.id }
            Cooldown(skill, CooldownManager.remainingTicks(player, skill), item?.type ?: skill.itemMaterial ?: Material.RED_DYE)
        }
        return Frame(player.location.clone(), player.velocity.clone(), player.isSneaking, player.isSprinting,
            player.inventory.heldItemSlot, cooldowns,
            data.statusAbnormalitys.filter { it.isClassMechanic && it.effectSource?.classId != "cartoonist" }
                .map { Resource(it, it.power, it.duration) },
            abilities.associate { it.abilityScope to it.abilityScope.resources.checkpoint() })
    }

    private fun clone(data: PlayerData): Mannequin = data.player.world.spawn(data.player.location, Mannequin::class.java) { echo ->
        echo.addScoreboardTag("cw-cartoon-frame")
        echo.isPersistent = false; echo.isInvulnerable = true; echo.isSilent = true
        echo.isCollidable = false; echo.isImmovable = true; echo.setGravity(false); echo.setAI(false); echo.setCanPickupItems(false)
        echo.profile = ResolvableProfile.resolvableProfile(data.player.playerProfile)
        echo.description = null
        echo.setPose(data.player.pose, true)
        echo.equipment.armorContents = data.player.inventory.armorContents.map { it?.clone() }.toTypedArray()
        echo.equipment.setItemInMainHand(data.player.inventory.itemInMainHand.clone())
        echo.equipment.setItemInOffHand(data.player.inventory.itemInOffHand.clone())
    }

    private fun safeLocation(data: PlayerData, recorded: Location): Location? {
        if (recorded.world != data.player.world || !recorded.world.worldBorder.isInside(recorded)) return null
        for (up in 0..3) {
            val at = recorded.clone().add(0.0, up.toDouble(), 0.0)
            val box = data.player.boundingBox.clone().shift(at.toVector().subtract(data.player.location.toVector()))
            if (!at.world.hasCollisionsIn(box)) return at
        }
        return null
    }

    companion object {
        private val sessions = mutableSetOf<CartoonRuntime.Session>()
        private val distortionCounts = mutableMapOf<UUID, Int>()
        private val executing = mutableSetOf<UUID>()
        private fun deferredSession(id: UUID) = if (id in executing) null else
            sessions.firstOrNull { it.forward && it.recording && !it.closed && id in it.tracks }
        fun isDeferring(id: UUID) = deferredSession(id) != null
        fun hasReservedCooldown(data: PlayerData, skill: Skill): Boolean =
            deferredSession(data.uniqueId)?.reservations?.ready(skill.id, data.game.combatTick) == false

        fun deferSkill(data: PlayerData, skill: Skill, item: ItemStack, ticks: Int): Boolean {
            val session = deferredSession(data.uniqueId) ?: return false
            if (!session.reservations.ready(skill.id, data.game.combatTick)) return false
            session.addAction(data, Action.Cast(skill, item.clone(), data.player.location.clone()))
            val effectiveTicks = CooldownManager.effectiveCooldownTicks(ticks, ClassBalanceManager.cooldownFlowMultiplier(data.player, skill))
            session.reservations.reserve(skill.id, data.game.combatTick, effectiveTicks)
            return true
        }

        fun deferAttack(context: DamageContext): Boolean {
            if (!context.path.isBasicAttack || context.secondaryAttack) return false
            val session = deferredSession(context.attacker.uniqueId) ?: return false
            val range = if (context.path == DamagePath.RANGED_ATTACK) 48.0 else 3.5
            session.addAction(context.attacker, Action.Attack(context.target, context.baseDamage, context.damageType,
                context.path, context.attacker.player.location.clone(), range, context.attackEffectsAllowed))
            return true
        }
        fun isDistorted(id: UUID) = (distortionCounts[id] ?: 0) > 0
        fun isLocked(id: UUID) = id !in executing && sessions.any { !it.recording && id in it.tracks }
        private fun <T> authorized(id: UUID, action: () -> T): T {
            val added = executing.add(id)
            try { return action() } finally { if (added) executing.remove(id) }
        }
        fun recordSkill(data: PlayerData, skill: Skill, item: ItemStack, origin: Location) {
            if (data.uniqueId in executing || skill.ownerClass.classId == "cartoonist") return
            sessions.toList().forEach { it.addAction(data, Action.Cast(skill, item.clone(), origin.clone())) }
        }
        fun recordGesture(id: UUID) { if (id !in executing) sessions.filter { it.recording }.forEach { it.tracks[id]?.changed = true } }
        fun recordAttack(context: DamageContext) {
            if (context.attacker.uniqueId in executing || !context.path.isBasicAttack || context.secondaryAttack) return
            val player = context.attacker.player
            val range = if (context.path == DamagePath.RANGED_ATTACK) 48.0 else 3.5
            sessions.toList().forEach { it.addAction(context.attacker,
                Action.Attack(context.target, context.baseDamage, context.damageType, context.path, player.location.clone(), range, context.attackEffectsAllowed)) }
        }
    }
}
