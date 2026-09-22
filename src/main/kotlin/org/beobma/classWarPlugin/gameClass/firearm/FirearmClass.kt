package org.beobma.classWarPlugin.gameClass.firearm

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.game.CooperativeAction
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.util.*
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import kotlin.math.*
import kotlin.random.Random

abstract class FirearmClass(val firearmProfile: FirearmProfile) : GameClass(), GameStatusHandler,
    WeaponInputHandler, ConfirmedHitHandler, BorrowableFirearm {
    final override var reloadDisabled = false
    final override var onMagazineEmpty: (() -> Unit)? = null
    var weaponOwnerId: String? = null
    private var magazine = FirearmMagazine(firearmProfile)
    final override val ammunition get() = magazine.bullets
    final override val reloadSkillIds get() = setOf("$classId/red-skill")
    private var ticks = 0L
    private var lastCombat = 0L
    private var lastHealth = 20.0
    private var trigger = false
    private var recoilPitch = 0f
    private var recoilYaw = 0f
    private var speed: AttributeEffects.Lease? = null
    private var jump: AttributeEffects.Lease? = null
    private lateinit var display: AmmoDisplay
    private var lastAmmoFrame: String? = null

    fun isHoldingGun(): Boolean {
        val item = player.inventory.itemInMainHand
        val tag = getWeaponClassId(item)
        return tag == (weaponOwnerId ?: classId) || (tag == null && item.type == weapon.material)
    }
    fun blocksJump() = firearmProfile == FirearmProfile.MINIGUN && (isHoldingGun() || magazine.reloading)
    private fun canFire() = abilityScope.started && !abilityScope.isClosed && !abilityScope.suspended &&
        abilityScope.isActive && !game.isPaused && !playerStatus.isDead && playerStatus.canAttack &&
        playerStatus.canSkillUse && !playerData.hasStatus<Disarm>() &&
        game.canPerform(playerData.uniqueId, CooperativeAction.BASIC_ATTACK) && isHoldingGun()

    override fun onBattleStart() {
        magazine = FirearmMagazine(firearmProfile)
        ticks = 0; lastCombat = 0; lastHealth = player.health
        display = AmmoDisplay(magazine)
        playerData.addStatus(display, playerData)
        display.updatePower(1)
        lastAmmoFrame = display.actionBarText()
        speed = playerData.attributeEffects.walkSpeed(abilityScope, 1.0)
        jump = playerData.attributeEffects.multiply(abilityScope, Attribute.JUMP_STRENGTH, 1.0)
        abilityScope.resources.own {
            stopTrigger(); recoilPitch = 0f; recoilYaw = 0f
            if (player.isOnline && isHoldingGun()) player.clearActiveItem()
        }
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                ticks++
                if (player.health < lastHealth) lastCombat = ticks
                lastHealth = player.health
                if (magazine.tick()) {
                    sounds.playTo(player, Sound.BLOCK_IRON_TRAPDOOR_OPEN, volume = 0.6f, pitch = 1.7f)
                    particles.spawn(player.eyeLocation.clone().add(0.0, -0.35, 0.0), Particle.CRIT, count = 5, spread = 0.1)
                }
                if (firearmProfile == FirearmProfile.SMG && !reloadDisabled && !magazine.reloading &&
                    (ammunition == 0 || (ammunition < firearmProfile.capacity && ticks - lastCombat >= 120))) beginReload(preserveTrigger = true)
                refreshMovement()
                if (trigger && (!canFire() || !player.hasActiveItem() || player.activeItemHand != EquipmentSlot.HAND)) stopTrigger()
                if (trigger) fire()
                refreshAmmoDisplay()
                if (abs(recoilPitch) + abs(recoilYaw) > 0.01f && isHoldingGun()) {
                    val pitch = recoilPitch * 0.45f; val yaw = recoilYaw * 0.45f
                    val at = player.location
                    player.setRotation(io.papermc.paper.math.Angle.relative(yaw),
                        io.papermc.paper.math.Angle.relative((at.pitch - pitch).coerceIn(-89.9f, 89.9f) - at.pitch))
                    recoilPitch -= pitch; recoilYaw -= yaw
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1, 1)
    }

    override fun onSuspend() {
        stopTrigger(); speed?.setMultiplier(1.0); jump?.setMultiplier(1.0); recoilPitch = 0f; recoilYaw = 0f
        if (player.isOnline && isHoldingGun()) player.clearActiveItem()
    }
    override fun onGameTimePasses() {} // Magazine and held input use the combat-tick task above.
    override fun onResume() { refreshMovement(); refreshAmmoDisplay(force = true) }
    override fun onConfirmedHit(context: DamageContext) { lastCombat = ticks }
    override fun onConfirmedDamageTaken(context: DamageContext) { lastCombat = ticks }
    fun stopTrigger() { trigger = false }
    fun canReload() = !reloadDisabled && !magazine.reloading
    fun beginReload(preserveTrigger: Boolean = false): Boolean {
        if (!canReload()) return false
        if (!preserveTrigger) {
            stopTrigger()
            if (isHoldingGun()) player.clearActiveItem()
        }
        magazine.reload(org.beobma.classWarPlugin.growth.GrowthScaling.cooldown(playerData, firearmProfile.reloadTicks, classId))
        refreshAmmoDisplay()
        sounds.playTo(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, volume = 0.65f, pitch = 0.85f)
        refreshMovement()
        return true
    }
    private fun refreshAmmoDisplay(force: Boolean = false) {
        if (!::display.isInitialized) return
        val frame = display.actionBarText()
        if (!force && frame == lastAmmoFrame) return
        lastAmmoFrame = frame
        // Rebuild the shared action bar so other class resources and debuffs stay visible.
        playerData.updateStatusActionBar()
    }
    private fun refreshMovement() {
        val slowing = when {
            magazine.reloading -> firearmProfile.reloadSlow
            firearmProfile == FirearmProfile.MINIGUN && isHoldingGun() -> 0.8
            else -> 0.0
        }
        val scaledSlow = slowing * org.beobma.classWarPlugin.growth.GrowthScaling.multiplier(
            playerData, org.beobma.classWarPlugin.growth.GrowthAxis.SPEED, classId)
        speed?.setMultiplier((1.0 - scaledSlow).coerceAtLeast(0.0))
        jump?.setMultiplier(if (blocksJump()) 0.0 else 1.0)
    }
    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        if (!canFire() || magazine.reloading) return
        if (ammunition < firearmProfile.cost) {
            sounds.playTo(player, Sound.BLOCK_DISPENSER_FAIL, volume = 0.4f, pitch = 1.3f)
            return
        }
        fire()
        if (firearmProfile.automatic && (ammunition > 0 || (firearmProfile == FirearmProfile.SMG && !reloadDisabled))) {
            trigger = true
            event.setUseItemInHand(Event.Result.ALLOW)
        }
    }
    override fun onWeaponInteractEntity(event: PlayerInteractEntityEvent) {
        event.isCancelled = true
        if (!canFire() || magazine.reloading) return
        fire()
        if (firearmProfile.automatic && (ammunition > 0 || (firearmProfile == FirearmProfile.SMG && !reloadDisabled))) {
            trigger = true
            player.startUsingItem(EquipmentSlot.HAND)
        }
    }

    private fun fire() {
        if (!canFire() || !magazine.shoot(ticks)) return
        refreshAmmoDisplay()
        lastCombat = ticks
        val start = player.eyeLocation.clone()
        val forward = start.direction.normalize()
        val targets = Targeting.select(playerData, TargetType.Enemy, start.world, includeStealth = false)
        val hits = linkedMapOf<org.beobma.classWarPlugin.entity.EntityData, Int>()
        val range = org.beobma.classWarPlugin.manager.ClassBalanceManager.scaleRange(playerData, firearmProfile.range)
        repeat(firearmProfile.pellets) { pellet ->
            val direction = spreadDirection(forward, firearmProfile.spread, Random.Default)
            val block = start.world.rayTraceBlocks(start, direction, range, FluidCollisionMode.NEVER, true)
            val limit = block?.hitPosition?.distance(start.toVector()) ?: range
            val hit = targets.mapNotNull { target ->
                HitboxUtil.rayIntersectionDistance(target.entity.boundingBox, start.toVector(), direction, limit)
                    ?.takeIf { it < limit }?.let { target to it }
            }.minByOrNull { it.second }
            val end = start.clone().add(direction.clone().multiply(hit?.second ?: limit))
            if (hit != null) hits[hit.first] = ((hits[hit.first] ?: 0) + 1).coerceAtMost(if (firearmProfile == FirearmProfile.SHOTGUN) 8 else 1)
            if (firearmProfile != FirearmProfile.SHOTGUN || pellet % 2 == 0)
                particles.line(start.clone().add(direction.clone().multiply(0.5)), end, Particle.CRIT, spacing = 1.8)
            if (hit != null) particles.spawn(end, Particle.CRIT, count = 2, spread = 0.04)
            else if (block != null && pellet % 3 == 0) particles.spawn(end, Particle.SMOKE, count = 2, spread = 0.05)
        }
        hits.forEach { (target, count) ->
            target.damage(firearmProfile.damageForHits(count), DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
        }
        val muzzle = start.clone().add(forward.clone().multiply(0.65))
        particles.spawn(muzzle, Particle.FLAME, count = if (firearmProfile == FirearmProfile.SHOTGUN) 5 else 1, spread = 0.035, speed = 0.01)
        particles.spawn(muzzle, Particle.SMOKE, count = 2, spread = 0.08, speed = 0.015)
        val pitch = when (firearmProfile) { FirearmProfile.SHOTGUN -> 0.65f; FirearmProfile.ASSAULT -> 1.2f; FirearmProfile.SMG -> 1.7f; FirearmProfile.MINIGUN -> 0.9f }
        sounds.play(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, volume = if (firearmProfile == FirearmProfile.SHOTGUN) 0.95f else 0.4f, pitch = pitch)
        recoilPitch = (recoilPitch + firearmProfile.recoil).coerceAtMost(18f)
        recoilYaw = (recoilYaw + Random.nextDouble(-0.35, 0.35).toFloat() * firearmProfile.recoil).coerceIn(-4f, 4f)
        if (firearmProfile == FirearmProfile.SHOTGUN) player.velocity = player.velocity.add(forward.clone().multiply(-0.38)).setY(player.velocity.y.coerceAtLeast(0.1))
        if (ammunition == 0) {
            if (firearmProfile != FirearmProfile.SMG || reloadDisabled) stopTrigger()
            onMagazineEmpty?.invoke()
        }
    }

    private class AmmoDisplay(val magazine: FirearmMagazine) : StatusAbnormality() {
        override val name = Keyword.Bullet.string
        override val description = listOf("<gray>탄창·탄띠의 밝은 탄환은 잔탄이며, 어두운 탄환은 빈 자리이다.",
            "<gray>재장전 중에는 탄창 교체·탄 삽입·잠금 동작을 표시한다.")
        override val canRemove = false
        override val isClassMechanic = true
        override var duration: Int? = null
        override fun actionBarText(): String = FirearmHud.render(magazine)
    }
    companion object {
        fun spreadDirection(forward: Vector, spread: Double, random: Random): Vector {
            val f = forward.clone().normalize()
            var right = f.clone().crossProduct(Vector(0.0, 1.0, 0.0))
            if (right.lengthSquared() < 1e-8) right = f.clone().crossProduct(Vector(1.0, 0.0, 0.0))
            right.normalize()
            val up = right.clone().crossProduct(f).normalize()
            val angle = random.nextDouble() * PI * 2
            val radius = sqrt(random.nextDouble()) * spread
            return f.add(right.multiply(cos(angle) * radius)).add(up.multiply(sin(angle) * radius)).normalize()
        }
    }
}
