package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.effect.CombatVisuals
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.gameClass.Weapon
import org.beobma.classWarPlugin.gameClass.agent.AgentState
import org.beobma.classWarPlugin.gameClass.agent.AgentWeaponStats
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId
import org.beobma.classWarPlugin.manager.GameClassManager.toWeaponItemStack
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.skill.MovementSkill
import org.beobma.classWarPlugin.status.list.CaduceusStatus
import org.beobma.classWarPlugin.status.list.DirectiveStatus
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.status.list.Fix
import org.beobma.classWarPlugin.status.list.Stun
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.HitboxUtil
import org.beobma.classWarPlugin.util.TargetType
import org.beobma.classWarPlugin.keyword.Keyword
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import java.util.UUID
import org.beobma.classWarPlugin.skill.Passive as BasePassive

class Agent : GameClass(), GameStatusHandler, OnHitHandler, WhenHitHandler, ConfirmedHitHandler, WeaponInputHandler, EnvironmentalDamageHandler {
    override val classId = "agent"
    override val name = "<gray>대행자"
    override val rank = Rank.L
    override val classItemMaterial = Material.HEART_OF_THE_SEA
    private enum class Form(val label: String, val item: Material, val reach: Double = 1.0, val speed: Double = 1.0) {
        HATCHET("손도끼", Material.IRON_AXE), STILETTO("스틸레토", Material.IRON_SWORD),
        BASTARD("바스타드 소드", Material.DIAMOND_SWORD, 1.1),
        RAPIER("레이피어", Material.GOLDEN_SWORD, speed = 2.5),
        HAMMER("망치", Material.MACE), GREATSWORD("대검", Material.NETHERITE_SWORD, speed = .55),
        LANCE("랜스", Material.TRIDENT), WHIP("채찍", Material.IRON_CHAIN, 4.0),
        SCYTHE("낫", Material.IRON_HOE, .5);
        val keyword: Keyword get() = when (this) {
            HATCHET -> Keyword.AgentHatchet; STILETTO -> Keyword.AgentStiletto; BASTARD -> Keyword.AgentBastard
            RAPIER -> Keyword.AgentRapier; HAMMER -> Keyword.AgentHammer; GREATSWORD -> Keyword.AgentGreatsword
            LANCE -> Keyword.AgentLance; WHIP -> Keyword.AgentWhip; SCYTHE -> Keyword.AgentScythe
        }
    }
    private var form = Form.HATCHET
    override val weapon: Weapon = object : Weapon() {
        override val name get() = "${Keyword.Caduceus.string} · ${form.keyword.string}"
        // A model changes appearance without enabling material-specific attacks (e.g. mace smash damage).
        override val material = Material.IRON_SWORD
        override val description get() = listOf("<gray>3회 적중하거나 10초가 지나면 다른 무기로 변형한다.",
            "<gray>외형과 관계없이 기본 피해는 철 검을 기준으로 계산한다.",
            form.keyword.requireDescription(),
            "<gray>손도끼일 때 무기를 우클릭하면 도약 강타를 사용한다. (재사용 대기시간은 4초)")
    }
    override var skills: List<Skill> = emptyList()
    private val slam = Slam()

    override var passives: List<BasePassive> = listOf(
        Passive(), PassiveTwo()
    )

    private val state = AgentState()
    private var reachLease: AttributeEffects.Lease? = null
    private var speedLease: AttributeEffects.Lease? = null
    private var baseAttackSpeed = 4.0
    private var movementLease: AttributeEffects.Lease? = null
    private var previousLocation: Location? = null
    private var sprintDistance = 0.0
    private var comboTarget: UUID? = null
    private var comboHits = 0
    private var slamActive = false
    private var resolvingSlamHit = false
    private var directiveLabel = "지령 대기"
    private var directiveEnemies = emptyList<EntityData>()
    private var pendingMorph = false

    override fun onBattleStart() {
        // No skill item is issued, but the weapon action still uses normal skill validation and cooldowns.
        slam.bind(playerData, this)
        abilityScope.resources.own { CooldownManager.resetCooldown(player, slam) }
        state.reset(game.combatTick)
        comboHits = 0; comboTarget = null; sprintDistance = 0.0; pendingMorph = false; slamActive = false
        directiveEnemies = emptyList(); previousLocation = player.location.clone()
        reachLease = playerData.attributeEffects.multiply(abilityScope, Attribute.ENTITY_INTERACTION_RANGE, 1.0)
        baseAttackSpeed = player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue ?: 4.0
        speedLease = playerData.attributeEffects.multiply(abilityScope, Attribute.ATTACK_SPEED, 1.0)
        movementLease = playerData.attributeEffects.multiply(abilityScope, Attribute.MOVEMENT_SPEED, 1.0)
        morph(Form.entries.random())
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                val now = game.combatTick
                val here = player.location
                val old = previousLocation
                if (holdingWeapon() && form == Form.LANCE && player.isSprinting && old?.world == here.world) {
                    val delta = here.clone().subtract(old).toVector().setY(0.0).length()
                    if (delta <= 1.5) sprintDistance = (sprintDistance + delta).coerceAtMost(20.0)
                    else sprintDistance = 0.0 // Teleports do not charge the lance.
                } else sprintDistance = 0.0
                previousLocation = here.clone()
                if (!slamActive && (pendingMorph || now >= state.nextMorph)) morph()
                updateAttributes()
                if ((state.directive == 0 && now >= state.deadline || state.resolved) && now % 20L == 0L) issueDirective()
                if (state.directive != 0) {
                    val allDefeated = directiveEnemies.isNotEmpty() && directiveEnemies.all {
                        it.entity.isDead || it.entityStatus.isDead
                    }
                    state.result(now, allDefeated)?.let(::resolveDirective)
                }
                if (now % 4L == 0L) updateHud()
            }
            override fun onCancel() { slamActive = false; previousLocation = null; directiveEnemies = emptyList() }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
        updateHud()
    }
    override fun onGameTimePasses() {}
    override fun onSuspend() {
        if (abilityScope.started) CooldownManager.pauseCooldown(player, slam)
        reachLease?.setMultiplier(1.0); speedLease?.setMultiplier(1.0); movementLease?.setMultiplier(1.0)
        previousLocation = null; sprintDistance = 0.0
    }
    override fun onResume() {
        if (abilityScope.started) CooldownManager.resumeCooldown(player, slam)
        previousLocation = player.location.clone(); updateAttributes()
    }
    private fun enemies() = Targeting.select(playerData, TargetType.Enemy)
    private fun isDirectiveTarget(target: EntityData): Boolean =
        Targeting.isEnemy(playerData, target) && (target is PlayerData || PlayerTagManager.isTraining(player))
    private fun directiveTargets() = enemies().filter(::isDirectiveTarget)
    private fun holdingWeapon() = getWeaponClassId(player.inventory.itemInMainHand) == classId
    private fun updateAttributes() {
        val held = holdingWeapon()
        reachLease?.setMultiplier(if (held) form.reach else 1.0)
        speedLease?.setMultiplier(if (held) AgentWeaponStats.swordSpeedMultiplier(baseAttackSpeed, form.speed) else 1.0)
        movementLease?.setMultiplier(if (held && form == Form.LANCE) 1.2 else 1.0)
    }
    private fun morph(next: Form = Form.entries.filter { it != form }.random()) {
        form = next; state.morphed(game.combatTick); pendingMorph = false
        comboTarget = null; comboHits = 0; sprintDistance = 0.0
        // Replace only this class's tagged weapon, never unrelated inventory items or skills.
        for (slot in 0 until player.inventory.size) {
            val item = player.inventory.getItem(slot) ?: continue
            if (getWeaponClassId(item) == classId) {
                val replacement = toWeaponItemStack(player)
                replacement.itemMeta = replacement.itemMeta.apply { AgentWeaponStats.applyAppearance(this, form.item) }
                player.inventory.setItem(slot, replacement)
            }
        }
        updateAttributes()
        CombatVisuals.pulse(abilityScope, player.location.add(0.0, 1.0, 0.0), Vector(0.0, 1.0, 0.0), .9, CombatVisuals.GOLD)
        sounds.play(player, Sound.ITEM_ARMOR_EQUIP_IRON, volume = .55f, pitch = 1.5f)
    }
    override fun onHit(context: DamageContext) { context.addDamageDealtMultiplier(state.dealtMultiplier) }
    override fun whenHit(context: DamageContext) { context.addDamageTakenMultiplier(state.takenMultiplier) }
    override fun onEnvironmentalDamage(event: org.bukkit.event.entity.EntityDamageEvent) {
        if (event is org.bukkit.event.entity.EntityDamageByEntityEvent) {
            if (event.damager is org.bukkit.entity.Player ||
                (event.damager as? org.bukkit.entity.Projectile)?.shooter is org.bukkit.entity.Player) return
        }
        event.damage *= state.takenMultiplier
    }
    override fun onAttackHit(context: DamageContext) {
        if (context.secondaryAttack || context.path != DamagePath.BASIC_ATTACK) return
        state.basicUsed()
        if (!holdingWeapon()) return
        when (form) {
            Form.STILETTO -> {
                context.armorIgnoreRatio = 1.0 - (1.0 - context.armorIgnoreRatio.coerceIn(0.0, 1.0)) * .8
                if (isBehind(context.target)) context.addBaseDamage(1.0)
            }
            Form.BASTARD -> context.addBaseDamage(if (comboTarget == context.target.entity.uniqueId) (comboHits + 1) * .5 else .5)
            Form.RAPIER -> context.addDamageDealtMultiplier(.75)
            Form.HAMMER -> context.shieldDamageMultiplier *= 2.0
            Form.LANCE -> context.addBaseDamage(sprintDistance * .2)
            else -> Unit
        }
    }
    override fun onConfirmedHit(context: DamageContext) {
        if (context.secondaryAttack) return
        val basic = context.path == DamagePath.BASIC_ATTACK
        if (game.combatTick < state.deadline && isDirectiveTarget(context.target)) {
            state.hit(context.target.entity.uniqueId, basic, context.path == DamagePath.SKILL && resolvingSlamHit,
                if (holdingWeapon()) form.ordinal else -1, isBehind(context.target), player.isSprinting)
        }
        if (!basic || !holdingWeapon()) return
        val target = context.target
        val direction = target.entity.location.toVector().subtract(player.location.toVector()).setY(0.0)
        if (direction.lengthSquared() > .0001) direction.normalize() else direction.setZ(1.0)
        when (form) {
            Form.HATCHET -> target.entity.velocity = direction.clone().multiply(.35).setY(.16)
            Form.HAMMER -> {
                target.entity.velocity = direction.clone().multiply(.95).setY(.3)
                target.getOrCreateStatus(playerData) { Stun() }.applyStatus(duration = 1)
            }
            Form.BASTARD -> {
                comboHits = if (comboTarget == target.entity.uniqueId) comboHits + 1 else 1
                comboTarget = target.entity.uniqueId
            }
            Form.WHIP -> target.entity.velocity = direction.clone().multiply(-.4).setY(.12)
            Form.GREATSWORD -> sweep(target, context.baseDamage)
            Form.LANCE -> sprintDistance = 0.0
            Form.SCYTHE -> pierce(target, direction)
            else -> Unit
        }
        weaponImpact(target, direction)
        if (state.basicHit()) pendingMorph = true // Never change forms inside a damage dispatch.
    }
    private fun isBehind(target: EntityData): Boolean {
        val toAttacker = player.location.toVector().subtract(target.entity.location.toVector()).setY(0.0)
        return toAttacker.lengthSquared() > .0001 && target.entity.location.direction.setY(0.0).dot(toAttacker.normalize()) < -.35
    }
    private fun weaponImpact(target: EntityData, direction: Vector) {
        val center = target.entity.boundingBox.center.toLocation(target.entity.world)
        val up = Vector(0.0, 1.0, 0.0)
        val sound = when (form) {
            Form.HATCHET -> Sound.ENTITY_PLAYER_ATTACK_STRONG
            Form.STILETTO -> Sound.ITEM_TRIDENT_HIT
            Form.BASTARD -> Sound.ENTITY_PLAYER_ATTACK_CRIT
            Form.RAPIER -> Sound.BLOCK_AMETHYST_BLOCK_HIT
            Form.HAMMER -> Sound.ITEM_MACE_SMASH_GROUND
            Form.GREATSWORD -> Sound.ENTITY_PLAYER_ATTACK_SWEEP
            Form.LANCE -> Sound.ITEM_TRIDENT_RIPTIDE_1
            Form.WHIP -> Sound.ENTITY_FISHING_BOBBER_RETRIEVE
            Form.SCYTHE -> Sound.ENTITY_PHANTOM_FLAP
        }
        sounds.play(center, sound, volume = .5f, pitch = when (form) {
            Form.RAPIER, Form.STILETTO -> 1.8f
            Form.HAMMER, Form.GREATSWORD -> .65f
            else -> 1.1f
        })
        when (form) {
            Form.HATCHET -> {
                CombatVisuals.slash(abilityScope, center, direction, .85, CombatVisuals.GOLD, tilt = 1.15)
                particles.spawn(center, Particle.CRIT, count = 9, spread = .2)
            }
            Form.STILETTO -> {
                CombatVisuals.tracer(center.clone().subtract(direction.clone().multiply(.7)), center.clone().add(direction), CombatVisuals.SILVER)
                particles.spawn(center, Particle.ENCHANTED_HIT, count = 7, spread = .12)
            }
            Form.BASTARD -> {
                CombatVisuals.slash(abilityScope, center, direction, 1.2, CombatVisuals.GOLD, tilt = .35)
                CombatVisuals.ring(center, direction, .3 + minOf(comboHits, 3) * .12, CombatVisuals.GOLD, 16)
            }
            Form.RAPIER -> {
                val side = CombatVisuals.plane(direction).first
                for (index in -1..1) {
                    val point = center.clone().add(side.clone().multiply(index * .15))
                    CombatVisuals.tracer(point.clone().subtract(direction.clone().multiply(.5)), point.clone().add(direction.clone().multiply(.7)), CombatVisuals.CYAN)
                }
            }
            Form.HAMMER -> {
                CombatVisuals.pulse(abilityScope, target.entity.location.add(0.0, .1, 0.0), up, 1.3, CombatVisuals.GOLD)
                particles.spawn(center, Particle.CRIT, count = 18, spread = .45, speed = .1)
            }
            Form.GREATSWORD -> particles.spawn(center, Particle.CLOUD, count = 10, spread = .45, speed = .04)
            Form.LANCE -> {
                CombatVisuals.tracer(player.location.add(0.0, 1.0, 0.0), center.clone().add(direction), CombatVisuals.CYAN)
                particles.spawn(center, Particle.ELECTRIC_SPARK, count = 12, spread = .18, speed = .06)
            }
            Form.WHIP -> {
                val start = player.location.add(0.0, 1.0, 0.0)
                val delta = center.toVector().subtract(start.toVector())
                val side = CombatVisuals.plane(delta).first
                val dust = Particle.DustOptions(CombatVisuals.VIOLET, .9f)
                repeat(25) { index ->
                    val t = index / 24.0
                    particles.spawn(start.clone().add(delta.clone().multiply(t))
                        .add(side.clone().multiply(kotlin.math.sin(t * Math.PI * 4) * .25)), Particle.DUST, dust)
                }
            }
            Form.SCYTHE -> {
                CombatVisuals.slash(abilityScope, center, direction, 1.1, CombatVisuals.VIOLET, tilt = -.8, reverse = true)
                particles.spawn(center, Particle.SOUL, count = 6, spread = .3, speed = .02)
            }
        }
    }
    private fun unlockEffect() {
        val center = player.location.add(0.0, 1.1, 0.0)
        sounds.play(center, Sound.BLOCK_GLASS_BREAK, volume = .7f, pitch = 1.35f)
        sounds.play(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, volume = .6f, pitch = 1.7f)
        object : AbilityRunnable(abilityScope) {
            var frame = 0
            override fun run() {
                val radius = .35 + frame * .25
                CombatVisuals.ring(center, Vector(0.0, 1.0, 0.0), radius, CombatVisuals.GOLD, 20)
                repeat(12) { index ->
                    val angle = index * Math.PI / 6
                    val shard = center.clone().add(kotlin.math.cos(angle) * radius, (index % 3 - 1) * frame * .12, kotlin.math.sin(angle) * radius)
                    particles.spawn(shard, Particle.END_ROD)
                    particles.spawn(shard, Particle.ELECTRIC_SPARK)
                }
                if (++frame == 4) {
                    sounds.playTo(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = .55f, pitch = 1.8f)
                    cancel()
                }
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 2L)
    }
    private fun sweep(primary: EntityData, damage: Double) {
        val origin = player.eyeLocation
        val direction = origin.direction
        enemies().filter { it !== primary && HitboxUtil.intersectsSphere(it.entity.boundingBox, origin.toVector(), 3.0) }
            .filter { HitboxUtil.closestPoint(it.entity.boundingBox, origin.toVector()).subtract(origin.toVector()).dot(direction) >= 0.0 }
            .filter { player.hasLineOfSight(it.entity) }.forEach {
                it.damage(damage, DamageType.Normal, playerData, damagePath = DamagePath.BASIC_ATTACK, secondaryAttack = true)
            }
        CombatVisuals.slash(abilityScope, player.location.add(0.0, 1.0, 0.0), direction, 3.0, CombatVisuals.GOLD)
        sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = .7f, pitch = .65f)
    }
    private fun pierce(target: EntityData, direction: Vector) {
        val start = player.location
        val destination = target.entity.location.clone().add(direction.clone().multiply(1.2))
        destination.yaw = start.yaw; destination.pitch = start.pitch
        val travel = destination.toVector().subtract(start.toVector())
        if (playerStatus.canMove && !playerData.hasStatus<Fix>() && travel.lengthSquared() <= 25.0 && (0..10).all { index ->
                val at = start.clone().add(travel.clone().multiply(index / 10.0))
                at.block.isPassable && at.clone().add(0.0, 1.0, 0.0).block.isPassable && at.world.worldBorder.isInside(at)
            }) {
            player.teleport(destination)
            CombatVisuals.tracer(start.clone().add(0.0, .9, 0.0), destination.clone().add(0.0, .9, 0.0), CombatVisuals.VIOLET)
        }
        // Confirmed-hit callbacks run before the original health subtraction; defer execution one tick.
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                val living = target.entity as? LivingEntity ?: return
                val max = living.getAttribute(Attribute.MAX_HEALTH)?.value ?: return
                if (living.isValid && !living.isDead && living.health < max * .1) {
                    if (living is org.bukkit.entity.Player && PlayerTagManager.isTraining(living)) {
                        living.sendMiniMessage("<dark_red>☤ 처형을 판정한다. 연습 모드에서는 사망하지 않는다.")
                    } else {
                        // Execution ignores invincibility and shields. The triggering hit already records its killer.
                        living.health = 0.0
                    }
                    particles.spawn(living.location.add(0.0, 1.0, 0.0), Particle.CRIT, count = 16, spread = .4)
                    sounds.play(living.location, Sound.ENTITY_WITHER_SKELETON_DEATH, volume = .5f, pitch = 1.5f)
                }
            }
        }.runTaskLater(ClassWarPlugin.instance, 1L)
    }
    override fun onWeaponLeftClick(event: PlayerInteractEvent) { state.basicUsed() }
    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        if (form == Form.HATCHET) playerData.use(slam, player.inventory.itemInMainHand)
    }
    override fun onWeaponInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        event.isCancelled = true
        if (form == Form.HATCHET) playerData.use(slam, player.inventory.itemInMainHand)
    }
    private inner class Slam : Skill(), MovementSkill {
        override val definitionId = "agent/slam"
        override val name = "<bold>도약 강타"
        override val description = listOf("<gray>손도끼 상태에서 무기를 우클릭하여 사용한다.",
            "<gray>짧게 도약한 후 내려찍어 반경 2.5칸의 적에게 2의 피해를 입힌다.")
        override val cooldown = 4
        override fun isUseSuccess() = holdingWeapon() && form == Form.HATCHET && playerStatus.canMove && !slamActive
        override fun use(): Boolean {
            slamActive = true
            val direction = player.location.direction.setY(0.0)
            if (direction.lengthSquared() > .001) direction.normalize()
            player.velocity = direction.multiply(.55).setY(.65)
            sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_STRONG, volume = .6f, pitch = .8f)
            object : AbilityRunnable(abilityScope) {
                var tick = 0
                override fun run() {
                    tick++
                    particles.spawn(player.location.add(0.0, .3, 0.0), Particle.CRIT, count = 3, spread = .2)
                    if (tick == 7 && playerStatus.canMove && !playerData.hasStatus<Fix>()) player.velocity = player.velocity.setY(-1.2)
                    val landed = player.world.rayTraceBlocks(player.location.clone().add(0.0, .15, 0.0), Vector(0.0, -1.0, 0.0), .35) != null
                    if (tick >= 8 && landed) {
                        val center = player.location
                        enemies().filter { HitboxUtil.intersectsSphere(it.entity.boundingBox, center.toVector(), 2.5) }
                            .filter { player.hasLineOfSight(it.entity) }
                            .forEach {
                                resolvingSlamHit = true
                                try { it.damage(2.0, DamageType.Normal, playerData, damagePath = DamagePath.SKILL) }
                                finally { resolvingSlamHit = false }
                            }
                        CombatVisuals.pulse(abilityScope, center.clone().add(0.0, .1, 0.0), Vector(0.0, 1.0, 0.0), 2.5, CombatVisuals.GOLD)
                        particles.spawn(center, Particle.CRIT, count = 24, spread = .8)
                        sounds.play(center, Sound.ENTITY_GENERIC_EXPLODE, volume = .5f, pitch = 1.4f)
                        cancel()
                    } else if (tick >= 60) cancel()
                }
                override fun onCancel() { slamActive = false }
            }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
            return true
        }
    }
    private fun issueDirective() {
        val candidates = directiveTargets()
        if (candidates.isEmpty()) return
        // A single remaining enemy (including solo training) cannot satisfy the two-target order.
        val retry = state.lastSucceeded == false
        if (!retry && state.nextDirective == 2 && candidates.size < 2) state.begin(game.combatTick, null, form.ordinal)
        val next = state.nextDirective
        // Skill-only orders must be possible even if a random transformation just removed the axe.
        if (next == 3 || next == 9) morph(Form.HATCHET)
        val chosen = candidates.firstOrNull { retry && it.entity.uniqueId == state.target }
            ?: if (next == 5) candidates.maxBy { (it.entity as LivingEntity).health } else candidates.random()
        val requiredWeapon = if (retry) state.requiredWeapon else form.ordinal
        if (retry && next == 4) morph(Form.entries[requiredWeapon])
        state.begin(game.combatTick, chosen.entity.uniqueId, requiredWeapon)
        directiveEnemies = if (state.directive == 10) game.playerDatas.filter {
            it.entity is LivingEntity && !it.entity.isDead && !it.entityStatus.isDead && isDirectiveTarget(it)
        }.toList() else candidates.toList()
        val targetName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(chosen.entity.name)
        directiveLabel = when (state.directive) {
            1 -> "${targetName}에게 피해를 입힌다."
            2 -> "서로 다른 적 두 명에게 피해를 입힌다."
            3 -> "6초간 기본 공격을 하지 않고 도약 강타를 적중시킨다."
            4 -> "${Form.entries[state.requiredWeapon].keyword.string}<white>로 적에게 피해를 입힌다."
            5 -> "체력이 가장 높았던 ${targetName}에게 피해를 입힌다."
            6 -> "기본 공격을 세 번 적중시킨다."
            7 -> "적의 뒤에서 공격한다."
            8 -> "달리는 중에 기본 공격을 적중시킨다."
            9 -> "도약 강타로 적에게 피해를 입힌다."
            else -> "모든 적을 처치한다. 기한은 게임이 종료될 때까지."
        }
        player.sendMiniMessage("<gold><bold>☤ 지령 ${state.directive}</bold> <white>$directiveLabel")
        sounds.playTo(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = .65f, pitch = 1.2f)
        updateHud()
    }
    private fun resolveDirective(success: Boolean) {
        state.resolve(success)
        sounds.playTo(player, if (success) Sound.ENTITY_PLAYER_LEVELUP else Sound.BLOCK_NOTE_BLOCK_BASS,
            volume = .6f, pitch = if (success) 1.4f else .6f)
        CombatVisuals.pulse(abilityScope, player.location.add(0.0, .2, 0.0), Vector(0.0, 1.0, 0.0), 1.4,
            if (success) CombatVisuals.GOLD else CombatVisuals.VIOLET)
        if (success) unlockEffect()
        // No targets: stop evaluating this resolved order while waiting for a valid next one.
        issueDirective()
    }
    private fun updateHud() {
        val now = game.combatTick
        val time = if (state.deadline == Long.MAX_VALUE) "∞" else "${((state.deadline - now).coerceAtLeast(0) + 19) / 20}초"
        val changes = listOf(
            playerData.getOrCreateStatus(playerData) { CaduceusStatus() }.synchronize(
                "${form.keyword.string} <gray>${state.basicHits}/3 · ${((state.nextMorph - now).coerceAtLeast(0) + 19) / 20}초</gray>"),
            playerData.getOrCreateStatus(playerData) { DirectiveStatus() }.synchronize(
                "<yellow>${state.directive}</yellow> <white>${if (state.directive == 0 || state.resolved) "지령을 기다린다." else directiveLabel}</white> <yellow>($time)</yellow>"),
        )
        if (changes.any { it }) playerData.updateStatusActionBar()
    }

    private class Passive : BasePassive() {
        override val name = "<bold>☤ | ${Keyword.Caduceus.string}"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>적에게 기본 공격 3회 적중 시 혹은 10초마다 자신의 무기가 무작위로 변형된다.",
            "<gray>무기별 효과는 아래와 같다.",
            "",
            "<gray>  - ${Keyword.AgentHatchet.string}: 적중 시 약하게 밀어낸다. 우클릭하면 도약하여 내려찍고 2의 피해를 입힌다.",
            "<gray>  - ${Keyword.AgentStiletto.string}: 방어력 20%를 무시한다. 배후 공격 시 피해 1을 추가한다.",
            "<gray>  - ${Keyword.AgentBastard.string}: 사거리가 10% 증가한다. 같은 적에게 연속 적중할 때마다 피해 0.5를 추가한다.",
            "<gray>  - ${Keyword.AgentRapier.string}: 피해가 25% 감소하고 공격 속도가 크게 증가한다.",
            "<gray>  - ${Keyword.AgentHammer.string}: 강하게 밀어내고 잠시 기절시킨다. {keyword:Shield}에 두 배의 피해를 입힌다.",
            "<gray>  - ${Keyword.AgentGreatsword.string}: 공격 속도가 감소한다. 적중 시 전방을 휩쓸어 적에게 피해를 입힌다.",
            "<gray>  - ${Keyword.AgentLance.string}: 이동 속도가 20% 증가한다. 달린 거리에 비례하여 추가 피해를 입힌다.",
            "<gray>  - ${Keyword.AgentWhip.string}: 사거리가 300% 증가한다. 적중 시 적을 약하게 끌어당긴다.",
            "<gray>  - ${Keyword.AgentScythe.string}: 사거리가 50% 감소한다. 적을 관통하여 이동하고 체력이 10% 미만이면 {keyword:Execution}한다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = Keyword.Directive.string
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 10초 후 혹은 지령을 완수할 때 새로운 지령을 받는다.",
            "<gray>제한 시간 안에 지령을 수행하면 영구적으로 가하는 피해가 20% 증가하고 받는 피해가 10% 감소한다.",
            "<gray>지령을 수행하지 못하면 영구적으로 가하는 피해가 5% 감소하고 받는 피해가 5% 증가한다.",
            "<gray>지령은 성공할 때만 다음 단계로 진행한다. 실패하면 같은 단계에 다시 도전한다.",
            "<gray>가하는 피해는 최소 50%, 받는 피해는 최대 150%로 제한한다.",
            "<gray>I: 10초 안에 지정된 적에게 피해를 입힌다.",
            "<gray>II: 8초 안에 서로 다른 적 두 명에게 피해를 입힌다.",
            "<gray>III: 6초간 기본 공격을 하지 않고 도약 강타를 적중시킨다.",
            "<gray>IV: 지정된 무기로 적에게 피해를 입힌다.",
            "<gray>V: 지령을 받을 때 체력이 가장 높은 적에게 피해를 입힌다.",
            "<gray>VI: 기본 공격을 세 번 적중시킨다.",
            "<gray>VII: 적의 뒤에서 공격한다.",
            "<gray>VIII: 달리는 중에 기본 공격을 적중시킨다.",
            "<gray>IX: 도약 강타로 적에게 피해를 입힌다.",
            "<gray>X: 제한 시간 없이 모든 적을 처치한다.",
            "<gray>III·IX를 받을 때는 손도끼로 변형된다. 그 외 지령의 제한 시간은 20초다.",
            "<gray>적이 1명만 남았다면 지령 II는 보상·불이익 없이 건너뛴다."
        )
    }
}
