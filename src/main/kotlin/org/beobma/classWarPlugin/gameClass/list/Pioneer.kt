package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.skill.Skill
import org.bukkit.Material
import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.ability.*
import org.beobma.classWarPlugin.damage.*
import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.handler.*
import org.beobma.classWarPlugin.gameClass.pioneer.PioneerState
import org.beobma.classWarPlugin.manager.CooldownManager
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.getConeTargets
import org.beobma.classWarPlugin.manager.SkillManager.getSkillId
import org.beobma.classWarPlugin.manager.SkillManager.use
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getOrCreateStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.updateStatusActionBar
import org.beobma.classWarPlugin.status.list.*
import org.beobma.classWarPlugin.util.*
import org.bukkit.Location
import org.beobma.classWarPlugin.effect.CombatVisuals
import org.bukkit.util.Vector
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val PIONEER_RED_SKILL_COOLDOWN_SECONDS = 8
private const val PIONEER_BLUE_SKILL_COOLDOWN_SECONDS = 20
private const val PIONEER_FORESIGHT_COST = 5
private const val PIONEER_YELLOW_SKILL_COOLDOWN_SECONDS = 90

class Pioneer : GameClass(), GameStatusHandler, ConfirmedHitHandler, WhenHitHandler, WeaponInputHandler, OtherSkillUseHandler, VibrationExplosionHandler {
    override val classId = "pioneer"
    override val name = "<gray>선각자"
    override val rank = Rank.S
    override val classItemMaterial = Material.ENDER_EYE
    override var skills: List<Skill> = listOf(
        RedSkill(),
        OrangeSkill(),
        YellowSkill()
    )

    override var passives: List<BasePassive> = listOf(
        Passive(),
        PassiveTwo(),
        PassiveThree()
    )

    private var state = PioneerState()
    private var evadeUntil = 0L
    private var counterTarget: UUID? = null
    private var counterUntil = 0L
    private var lastCombat = 0L
    private var nextAction = 0L
    private val previewTicks = mutableMapOf<UUID, Long>()
    private var nextPreviewSound = 0L
    private var nextImpactSound = 0L
    override fun onOtherPlayerSkillUse(event: org.beobma.classWarPlugin.event.PlayerSkillUseEvent) {
        if (event.context.skill !is org.beobma.classWarPlugin.skill.MovementSkill) return
        val moving = event.playerData.player
        val velocity = moving.velocity
        if (velocity.lengthSquared() < 0.001) return
        val start = moving.location
        val end = start.world.rayTraceBlocks(start.clone().add(0.0, 0.5, 0.0), velocity.clone().normalize(),
            (velocity.length() * 5).coerceAtMost(16.0))?.hitPosition?.toLocation(start.world)
            ?: start.clone().add(velocity.multiply(5.0))
        val delta = end.toVector().subtract(start.toVector())
        preview(event.playerData, (0..20).map { start.clone().add(delta.clone().multiply(it / 20.0)) })
    }
    private var speed: AttributeEffects.Lease? = null
    private var attackSpeed: AttributeEffects.Lease? = null
    private var appliedAcceleration = -1
    private data class Followup(val expires: Long, val stage: Int, val acceleration: Int)
    private val followups = mutableMapOf<UUID, Followup>()
    private var strikeEffect: ((EntityData) -> Unit)? = null
    private fun refresh() {
        if (appliedAcceleration != state.acceleration) {
            speed?.setMultiplier(1.0 + state.acceleration * 0.04)
            attackSpeed?.setMultiplier(1.0 + state.acceleration * 0.04)
            if (state.acceleration > appliedAcceleration && appliedAcceleration >= 0) {
                particles.circle(player.location.add(0.0, 0.15, 0.0), Particle.ELECTRIC_SPARK, 0.65, 12 + state.acceleration * 2)
                sounds.playTo(player, Sound.BLOCK_NOTE_BLOCK_PLING, volume = 0.4f, pitch = 0.8f + state.acceleration * 0.2f)
            }
            appliedAcceleration = state.acceleration
        }
        val tick = game.combatTick
        val changes = listOf(
            playerData.getOrCreateStatus(playerData) { ForesightStatus() }.apply {
                maxPower = growthCount("foresight", 30)
            }.synchronize(state.foresight),
            playerData.getOrCreateStatus(playerData) { AccelerationStatus() }
                .synchronize(state.acceleration, state.accelerationRemainingTicks(tick)),
            playerData.getOrCreateStatus(playerData) { AccelerationBulletStatus() }.synchronize(state.bullets),
            playerData.getOrCreateStatus(playerData) { DisposalStatus() }
                .synchronize(state.chainStage, state.chainRemainingTicks(tick)),
        )
        if (changes.any { it }) playerData.updateStatusActionBar()
    }
    override fun onBattleStart() {
        state = PioneerState(); lastCombat = game.combatTick
        state.foresight = growthCount("foresight", 30)
        speed = playerData.attributeEffects.walkSpeed(abilityScope, 1.0)
        attackSpeed = playerData.attributeEffects.multiply(abilityScope, Attribute.ATTACK_SPEED, 1.0)
        refresh()
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                state.expire(game.combatTick)
                followups.entries.removeIf { it.value.expires < game.combatTick }
                if (state.chainStage > 0 && game.combatTick >= state.chainExpires) endChain()
                refresh()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 1L)
    }
    override fun onGameTimePasses() {
        val maximum = growthCount("foresight", 30)
        state.foresight = state.foresight.coerceAtMost(maximum)
        if (game.combatTick - lastCombat >= 200) state.foresight = (state.foresight + 1).coerceAtMost(maximum)
    }
    override fun whenHit(context: DamageContext) {
        if (context.attacker == playerData || context.path == DamagePath.STATUS_EFFECT) return
        if (game.combatTick < evadeUntil) {
            context.isCancelled = true; evadeUntil = 0
            counterTarget = context.attacker.uniqueId; counterUntil = game.combatTick + 20
            particles.spawn(player, Particle.CLOUD, count = 12, spread = 0.3)
            particles.circle(player.location.add(0.0, 1.0, 0.0), Particle.END_ROD, 0.85, 24)
            CombatVisuals.pulse(abilityScope, player.location.add(0.0, 1.0, 0.0), player.eyeLocation.direction, 1.15, CombatVisuals.CYAN)
            sounds.play(player, Sound.ITEM_SHIELD_BLOCK, volume = 0.7f, pitch = 1.6f)
            sounds.playTo(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.5f, pitch = 1.9f)
        }
    }
    override fun onConfirmedDamageTaken(context: DamageContext) {
        lastCombat = game.combatTick
        state.foresight = (state.foresight - 3).coerceAtLeast(0)
    }
    override fun onConfirmedHit(context: DamageContext) {
        if (context.target == playerData) return
        lastCombat = game.combatTick
        state.hit(context.target.entity.uniqueId, game.combatTick)
        strikeEffect?.invoke(context.target)
        if (counterTarget == context.target.entity.uniqueId && game.combatTick <= counterUntil) {
            counterTarget = null; state.addBullets(1)
            later(context.target) {
                context.target.damage(2.0, DamageType.Normal, playerData)
                particles.spawn(context.target.entity, Particle.CRIT, count = 14, spread = 0.25, speed = 0.08)
                sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.6f, pitch = 1.5f)
            }
        }
        if (context.path.isBasicAttack && !context.secondaryAttack) {
            val follow = followups.remove(context.target.entity.uniqueId)
            if (follow != null && follow.expires >= game.combatTick) later(context.target) {
                val target = context.target
                if (follow.stage == 0 && follow.acceleration >= 3) {
                    val departure = player.location
                    val behind = target.entity.location.subtract(target.entity.location.direction.setY(0.0).normalize().multiply(1.2))
                    if (!playerData.hasStatus<Fix>() && playerStatus.canMove && behind.world == player.world &&
                        behind.distanceSquared(player.location) <= 64 && behind.block.isPassable && behind.clone().add(0.0, 1.0, 0.0).block.isPassable) player.teleport(behind)
                    target.damage(2.0, DamageType.Normal, playerData)
                    if (departure.world == player.world) particles.line(departure.clone().add(0.0, 1.0, 0.0), player.location.add(0.0, 1.0, 0.0), Particle.ELECTRIC_SPARK, 0.25)
                    particles.spawn(target.entity, Particle.SWEEP_ATTACK)
                    sounds.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, volume = 0.5f, pitch = 1.7f)
                    if (follow.acceleration == 5) { state.addBullets(1); explode(target) }
                } else if (follow.stage in 2..4 && state.spendBullets(2)) {
                    target.damage(2.0, DamageType.Normal, playerData)
                    target.getOrCreateStatus(playerData) { Vibration() }.applyStatus(duration = 10, powerDelta = 2)
                    particles.circle(target.entity.location.add(0.0, 1.0, 0.0), Particle.ELECTRIC_SPARK, 0.7, 20)
                    sounds.play(target.entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.6f, pitch = 1.8f)
                }
            }
            if (state.chainStage > 0) later { selectSkill(skills[2]) }
        }
    }
    private fun later(target: EntityData? = null, action: () -> Unit) {
        object : AbilityRunnable(abilityScope) {
            override fun run() {
                if (target != null && (!target.entity.isValid || target.entity.isDead || target.entityStatus.isDead || target.entity.world != player.world)) return
                action()
            }
        }.runTaskLater(ClassWarPlugin.instance, 1L)
    }
    private fun selectSkill(skill: Skill) {
        (0..8).firstOrNull { slot -> player.inventory.getItem(slot)?.let {
            getSkillId(it, player.uniqueId)?.let(skill::matchesId)
        } == true }?.let { player.inventory.heldItemSlot = it }
    }
    private fun selectWeapon() {
        (0..8).firstOrNull { slot -> player.inventory.getItem(slot)?.let {
            org.beobma.classWarPlugin.manager.GameClassManager.getWeaponClassId(it) == classId
        } == true }?.let { player.inventory.heldItemSlot = it }
    }
    private fun endChain() {
        state.endChain()
        val skill = skills[2]
        val item = player.inventory.contents.filterNotNull().firstOrNull { getSkillId(it, player.uniqueId)?.let(skill::matchesId) == true }
            ?: org.bukkit.inventory.ItemStack(Material.YELLOW_DYE)
        CooldownManager.setCooldown(player, skill, item, 90 * 20)
    }
    override fun onWeaponRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        val skill = if (state.chainStage > 0) skills[2] else skills[1]
        val item = player.inventory.contents.filterNotNull().firstOrNull { getSkillId(it, player.uniqueId)?.let(skill::matchesId) == true } ?: return
        playerData.use(skill, item)
    }
    private fun explode(target: EntityData, times: Int = 1) {
        target.addStatus(VibrationExplosion(times), playerData).applyStatus(duration = 1, powerDelta = 1)
    }
    override fun onVibrationExplosion(target: EntityData) {
        val impact = target.entity.boundingBox.center.toLocation(target.entity.world)
        CombatVisuals.pulse(abilityScope, impact, Vector(0.0, 1.0, 0.0), 1.65, CombatVisuals.CYAN)
        CombatVisuals.ring(impact, player.eyeLocation.direction, 0.85, CombatVisuals.SILVER)
        particles.circle(target.entity.location.add(0.0, 0.6, 0.0), Particle.ELECTRIC_SPARK, 1.0, 28)
        particles.spawn(target.entity, Particle.CRIT, count = 16, spread = 0.45, speed = 0.1)
        sounds.play(target.entity, Sound.BLOCK_AMETHYST_BLOCK_BREAK, volume = 0.65f, pitch = 0.7f)
        target.getStatus<Burn>()?.let { burn ->
            val duration = burn.duration ?: 0
            burn.remove(); target.entity.fireTicks = 0
            target.damage(duration.toDouble(), DamageType.StatusAbnormality, playerData)
            particles.spawn(target.entity, Particle.FLAME, count = 20, spread = 0.4, speed = 0.07)
            particles.spawn(target.entity, Particle.SMOKE, count = 8, spread = 0.3)
            CombatVisuals.pulse(abilityScope, impact, Vector(0.0, 1.0, 0.0), 2.0, CombatVisuals.GOLD)
            particles.spawn(impact, Particle.LAVA, count = 7, spread = 0.4, speed = 0.04)
            sounds.play(target.entity, Sound.ITEM_FIRECHARGE_USE, volume = 0.7f, pitch = 0.9f)
        }
    }
    private fun strike(stage: Int) {
        val dash = stage == 0 || stage == 1
        val start = player.location
        var destination = start.clone()
        if (dash) {
            val step = start.direction.setY(0.0)
            if (step.lengthSquared() > 0.001) {
                step.normalize().multiply(0.25)
                for (i in 1..12) {
                    val next = destination.clone().add(step)
                    if (!next.block.isPassable || !next.clone().add(0.0, 1.0, 0.0).block.isPassable) break
                    destination = next
                }
            }
        }
        val targets = if (dash) Targeting.select(playerData, TargetType.Enemy).filter {
            HitboxUtil.intersectsSegment(it.entity.boundingBox, start.toVector(), destination.toVector(), 0.9)
        } else playerData.getConeTargets(3.0, 100.0, TargetType.Enemy, false).filter { player.hasLineOfSight(it.entity) }
        if (dash && !player.teleport(destination)) return
        player.fallDistance = 0f
        if (dash) {
            particles.line(start.clone().add(0.0, 0.5, 0.0), destination.clone().add(0.0, 0.5, 0.0), Particle.FLAME, 0.25)
            particles.spawn(start, Particle.CLOUD, count = 8, spread = 0.3, speed = 0.03)
        }
        slashEffect(stage)
        strikeEffect = { target ->
            particles.spawn(target.entity, if (dash) Particle.FLAME else Particle.CRIT, count = 8, spread = 0.25, speed = 0.04)
            if (game.combatTick >= nextImpactSound) {
                nextImpactSound = game.combatTick + 4
                sounds.play(target.entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, volume = 0.4f, pitch = 1.1f + stage * 0.1f)
            }
            if (stage < 5) {
                if (dash) target.getOrCreateStatus(playerData) { Burn() }.applyStatus(duration = 2, powerDelta = 1)
                target.getOrCreateStatus(playerData) { Vibration() }.applyStatus(duration = 10, powerDelta = if (dash) 2 else 1)
                followups[target.entity.uniqueId] = Followup(game.combatTick + 20, stage, state.acceleration)
            } else later(target) { explode(target, 2) }
        }
        try { targets.forEach { it.damage(if (stage == 0) 3.0 else if (stage == 1) 2.0 else 1.0, DamageType.Normal, playerData) } }
        finally { strikeEffect = null }
        particles.spawn(player, Particle.SWEEP_ATTACK, count = 3, spread = 0.4)
        nextAction = game.combatTick + if (state.acceleration == 5) 2 else 4
        selectWeapon()
    }

    private fun slashEffect(stage: Int) {
        val center = player.location.add(0.0, 0.9, 0.0)
        val forward = center.clone().apply { pitch = 0f }.direction
        val radius = if (stage == 5) 2.8 else 2.0
        val color = if (stage <= 1) CombatVisuals.GOLD else CombatVisuals.CYAN
        CombatVisuals.slash(abilityScope, center, forward, radius, color,
            tilt = if (stage % 2 == 0) 0.55 else -0.55, reverse = stage % 2 != 0)
        for (angle in -50..50 step 10) {
            val point = center.clone().add(forward.clone().rotateAroundY(Math.toRadians(angle.toDouble())).multiply(radius))
            particles.spawn(point, if (stage <= 1) Particle.FLAME else Particle.ELECTRIC_SPARK)
        }
        sounds.play(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume = 0.7f, pitch = if (stage == 5) 0.65f else 1.0f + stage * 0.15f)
        if (stage == 1) sounds.playTo(player, Sound.BLOCK_BEACON_POWER_SELECT, volume = 0.5f, pitch = 0.8f)
        if (stage == 5) {
            CombatVisuals.slash(abilityScope, center, forward, radius, CombatVisuals.SILVER, tilt = -0.55, reverse = true)
            CombatVisuals.pulse(abilityScope, center, Vector(0.0, 1.0, 0.0), 2.8, CombatVisuals.CYAN)
            particles.circle(center, Particle.ELECTRIC_SPARK, 1.4, 32)
            sounds.play(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, volume = 0.5f, pitch = 0.65f)
            sounds.play(player, Sound.ITEM_TRIDENT_THUNDER, volume = 0.35f, pitch = 1.5f)
        }
    }

    /** A preview is visible only to this observer and costs once per caster action/tick. */
    fun preview(caster: PlayerData, points: List<Location>) {
        if (caster == playerData || !game.areEnemies(caster.uniqueId, playerData.uniqueId) || !player.isOnline ||
            playerStatus.isDead || abilityScope.suspended || game.isPaused || points.isEmpty() || points.first().world != player.world ||
            points.first().distanceSquared(player.location) > 48.0 * 48.0 || state.foresight < 2) return
        if (previewTicks[caster.uniqueId] != game.combatTick) {
            state.foresight -= 2
            previewTicks[caster.uniqueId] = game.combatTick
        }
        if (game.combatTick >= nextPreviewSound) {
            nextPreviewSound = game.combatTick + 10
            sounds.playTo(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume = 0.25f, pitch = 1.8f)
        }
        // Bound dense area forecasts, retaining both ends of the original sampled path.
        val visible = if (points.size <= 128) points.map { it.clone() } else
            (0 until 128).map { points[it * (points.size - 1) / 127].clone() }
        object : AbilityRunnable(abilityScope) {
            var frames = 0
            override fun run() {
                visible.filter { it.world == player.world }.forEachIndexed { index, point ->
                    particles.spawnTo(player, point, if (frames == 0) Particle.END_ROD else Particle.ELECTRIC_SPARK)
                    if ((index + frames) % 8 == 0) particles.spawnTo(player, point.clone().add(0.0, 0.1, 0.0), Particle.ENCHANT)
                }
                visible.lastOrNull()?.takeIf { it.world == player.world }?.let {
                    particles.spawnTo(player, it, Particle.ELECTRIC_SPARK, count = 6, spread = 0.2)
                }
                if (++frames >= 4) cancel()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 1L, 3L)
    }

    private inner class RedSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "pioneer/red-skill"
        override val name = "<bold>속검"
        override val description = listOf(
            "<gray>바라보는 방향으로 짧게 돌진하며 적을 베어 {g:damage:3}의 피해를 입힌다.",
            "<gray>적중 시 {g:duration:2}초간 {keyword:Burn} 상태로 만들고 {g:duration:10}초간 {keyword:Vibration}을 {g:physical-power:2} 부여한다.",
            "",
            "<gray>{keyword:Acceleration} 스택이 3 이상이라면",
            "<gray>스킬 적중 직후 기본 공격 적중 시 적의 뒤로 이동하며 추가로 {g:damage:2}의 피해를 입힌다.",
            "",
            "<gray>{keyword:Acceleration} 스택이 5라면",
            "<gray>스킬 적중 직후 기본 공격 적중 시 {keyword:AccelerationBullet}을 1 얻는다.",
            "<gray>또한 추가로 대상에게 {keyword:VibrationExplosion}을 적용한다.",
            "",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = PIONEER_RED_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (game.combatTick < nextAction) return false
            strike(0)
            return true
        }
    }

    // 0.2초 직전에 사용해야함
    private inner class OrangeSkill : Skill() {
        override val definitionId = "pioneer/orange-skill"
        override val name = "<bold>예지"
        override val description = listOf(
            "{keyword:Foresight} 스택을 $PIONEER_FORESIGHT_COST 소모하고 사용할 수 있다.",
            "",
            "<gray>적의 공격을 받기 직전에 스킬을 사용하면 해당 공격을 회피한다.",
            "<gray>회피에 성공 직후 공격자에게 피해를 입히면 {g:damage:2}의 추가 피해를 입히고 {keyword:AccelerationBullet}을 1 얻는다.",
            "",
            "<dark_gray>이 스킬 대신 검을 우클릭하여 사용할 수도 있다.",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다."
        )
        override val cooldown = PIONEER_BLUE_SKILL_COOLDOWN_SECONDS

        override fun isUseSuccess(): Boolean {
            if (game.combatTick < nextAction) return false
            if (!state.canSpendForesight(PIONEER_FORESIGHT_COST)) {
                player.sendMiniMessage("<red>예지안이 부족합니다. (필요: $PIONEER_FORESIGHT_COST, 보유: ${state.foresight})")
                return false
            }
            return true
        }

        override fun use(): Boolean {
            if (game.combatTick < nextAction) return false
            // Recheck after skill-use handlers; rejected/cancelled requests spend nothing.
            if (!state.spendForesight(PIONEER_FORESIGHT_COST)) return false
            evadeUntil = game.combatTick + 4
            refresh()
            particles.circle(player.location.add(0.0, 1.1, 0.0), Particle.ENCHANT, 0.65, 20)
            sounds.playTo(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume = 0.55f, pitch = 1.7f)
            selectWeapon()
            return true
        }
    }

    // 처분 궁극기
    private inner class YellowSkill : Skill(), org.beobma.classWarPlugin.skill.MovementSkill {
        override val definitionId = "pioneer/yellow-skill"
        override val name = Keyword.Disposal.string
        override val description = listOf(
            "<gray>{keyword:Disposal}은 5번까지 사용할 수 있다.",
            "",
            "<gray>1번째 사용 시 바라보는 방향으로 짧게 돌진하며 적을 베어 {g:damage:2}의 피해를 입힌다.",
            "<gray>적중 시 {g:duration:2}초간 {keyword:Burn} 상태로 만들고 {g:duration:10}초간 {keyword:Vibration}을 {g:physical-power:2} 부여한다.",
            "",
            "<gray>2~4번째 사용 시 바라보는 방향으로 검을 휘둘러 {g:damage:1}의 피해를 입힌다.",
            "<gray>적중 시 {g:duration:10}초간 {keyword:Vibration}을 {g:physical-power:1} 부여한다.",
            "<gray>사용 직후 기본 공격 적중 시 {keyword:AccelerationBullet}을 2 소모하여 위 효과와 피해를 2배로 다시 적용한다.",
            "",
            "<gray>5번째 사용 시 바라보는 방향으로 마지막 일격을 날려 {g:damage:1}의 피해를 입힌다.",
            "<gray>적중 시 {keyword:VibrationExplosion}을 2회 적용한다.",
            "<gray>위 효과로 {keyword:VibrationExplosion}이 2회 모두 적용될 때까지 적의 {keyword:Vibration}은 감소하지 않는다.",
            "",
            "<dark_gray>이 스킬 대신 검을 우클릭하여 사용할 수도 있다.",
            "<dark_gray>사용 후 핫바키가 1번으로 자동 교체된다.",
            "<dark_gray>최초 사용 후 10초간 기본 공격 적중 후 핫바키가 해당 스킬의 위치로 자동 교체된다.",
            "<dark_gray>최초 사용 후 10초 뒤에 남은 재사용 횟수와 관계 없이 이 스킬은 종료된다."
        )
        override val cooldown = PIONEER_YELLOW_SKILL_COOLDOWN_SECONDS

        override fun use(): Boolean {
            if (game.combatTick < nextAction) return false
            val stage = state.advanceChain(game.combatTick)
            if (stage == 0) { endChain(); return false }
            strike(stage)
            if (stage < 5) multiplyCurrentCooldown(0.0) else state.endChain()
            return true
        }
    }

    private class Passive : BasePassive() {
        override val name = Keyword.Foresight.string
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>게임 시작 시 {keyword:Foresight} 스택을 {g:feature/foresight:30} 얻는다.",
            "<gray>피격 시 {keyword:Foresight} 스택이 3 감소한다.",
            "<gray>전투에서 벗어난지 10초가 지나면 {keyword:Foresight} 스택은 천천히 {g:feature/foresight:30}까지 회복한다.",
            "",
            "<gray>{keyword:Foresight} 스택이 있으며, 적이 투사체, 순간이동, 이동 스킬, 공격 스킬을 발동할 때",
            "<gray>각각 아래의 효과를 발동하고 {keyword:Foresight} 스택이 2 감소한다.",
            "<gray>  - 투사체의 경우 궤적을 볼 수 있다.",
            "<gray>  - 순간이동의 경우 순간이동 도착 위치를 볼 수 있다.",
            "<gray>  - 이동 스킬의 경우 이동하는 거리와 도착 위치를 볼 수 있다.",
            "<gray>  - 공격 스킬의 경우 공격 스킬의 범위를 볼 수 있다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>미래 가속"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>같은 적에게 피해를 입힐 때마다 {keyword:Acceleration} 스택을 1 얻는다. (최대 스택 5, 6초마다 최대 1만 얻을 수 있음)",
            "<gray>다른 적에게 피해를 입히면 스택이 초기화되며, 4초간 같은 적에게 피해를 입히지 못하면 소멸한다.",
            "",
            "<gray>{keyword:Acceleration} 스택 1당 이동 속도와 공격 속도가 {g:speed-bonus:4}%씩 증가한다.",
            "<gray>{keyword:Acceleration} 스택이 5라면 스킬 사용 후 발생하는 딜레이가 감소한다."
        )
    }

    private class PassiveThree : BasePassive() {
        override val name = "<bold>작열"
        override val description = listOf(
            "<gray>패시브",
            "",
            "<gray>적에게 {keyword:VibrationExplosion}을 적용할 때",
            "<gray>대상이 {keyword:Burn} 상태라면 {keyword:Burn} 상태를 해제하고 지속시간에 비례한 {keyword:AbnormalStatusDamage}를 입힌다."
        )
    }
}
