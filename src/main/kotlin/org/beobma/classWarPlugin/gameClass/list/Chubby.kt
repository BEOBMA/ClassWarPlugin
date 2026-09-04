package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.EnvironmentalDamageHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.SkillManager.radius
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min
import org.beobma.classWarPlugin.skill.Passive as BasePassive

// 밸런스 조정 상수
private const val CHUBBY_MAX_HEALTH_MULTIPLIER = 2.0
private const val CHUBBY_JUMP_STRENGTH_MULTIPLIER = 1.0
private const val CHUBBY_SCALE_MULTIPLIER = 1.35
private const val CHUBBY_MOVE_SPEED_REDUCTION_PERCENT = 20
private const val CHUBBY_MIN_FALL_HEIGHT = 2.0
private const val CHUBBY_MAX_IMPACT_RADIUS = 5.0
private const val CHUBBY_BASE_IMPACT_RADIUS = 2.8
private const val CHUBBY_RADIUS_PER_FALL_BLOCK = 0.14
private const val CHUBBY_DAMAGE_FALL_OFFSET = 2.0
private const val CHUBBY_DAMAGE_PER_FALL_BLOCK = 1.4
private const val CHUBBY_MIN_IMPACT_DAMAGE = 2.0
private const val CHUBBY_MAX_IMPACT_DAMAGE = 18.0
private const val CHUBBY_BASE_HORIZONTAL_KNOCKBACK = 0.75
private const val CHUBBY_HORIZONTAL_KNOCKBACK_PER_BLOCK = 0.035
private const val CHUBBY_MAX_HORIZONTAL_KNOCKBACK = 1.5
private const val CHUBBY_BASE_VERTICAL_KNOCKBACK = 0.45
private const val CHUBBY_VERTICAL_KNOCKBACK_PER_BLOCK = 0.025
private const val CHUBBY_MAX_VERTICAL_KNOCKBACK = 0.95

class Chubby : GameClass(), GameStatusHandler, EnvironmentalDamageHandler, StatusPlayerMoveHandler {
    override val classId = "chubby"
    override val name = "<gray>뚱땡이"
    override val rank = Rank.A
    override val classItemMaterial = Material.COOKED_CHICKEN
    override var skills: List<Skill> = listOf()
    override var passives: List<BasePassive> = listOf(Passive())

    private var initialized = false
    private var airborne = false
    private var highestAirY = Double.NEGATIVE_INFINITY
    private var lastImpactTick = Long.MIN_VALUE

    override fun onBattleStart() {
        if (initialized) return
        initialized = true
        player.getAttribute(Attribute.MAX_HEALTH)?.let { attribute ->
            attribute.baseValue *= CHUBBY_MAX_HEALTH_MULTIPLIER
            player.health = attribute.value
        }
        player.getAttribute(Attribute.JUMP_STRENGTH)?.let { it.baseValue *= CHUBBY_JUMP_STRENGTH_MULTIPLIER }
        player.getAttribute(Attribute.SCALE)?.let { it.baseValue *= CHUBBY_SCALE_MULTIPLIER }
        playerData.addStatus(MoveSpeedDecrease(), playerData)
            .applyStatus(powerSet = CHUBBY_MOVE_SPEED_REDUCTION_PERCENT)
        particles.spawn(player, Particle.CLOUD, count = 26, spread = 0.8, speed = 0.08)
        sounds.play(player, Sound.ENTITY_HOGLIN_AMBIENT, volume = 0.75f, pitch = 0.7f)
    }

    override fun onGameTimePasses() = Unit

    override fun onEnvironmentalDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        val fallHeight = max(player.fallDistance.toDouble(), event.damage + 3.0)
        triggerImpact(fallHeight)
    }

    override fun onPlayerMove(event: PlayerMoveEvent, playerData: org.beobma.classWarPlugin.entity.player.PlayerData) {
        val destination = event.to
        val grounded = !destination.clone().subtract(0.0, 0.09, 0.0).block.isPassable
        if (!grounded) {
            if (!airborne) highestAirY = destination.y
            airborne = true
            highestAirY = max(highestAirY, destination.y)
            return
        }
        if (!airborne) return
        airborne = false
        val fallHeight = highestAirY - destination.y
        highestAirY = destination.y
        triggerImpact(fallHeight)
    }

    private fun triggerImpact(fallHeight: Double) {
        if (fallHeight < CHUBBY_MIN_FALL_HEIGHT) return
        val currentTick = game.combatTick
        if (lastImpactTick != Long.MIN_VALUE && currentTick - lastImpactTick <= 1L) return
        lastImpactTick = currentTick
        val radius = min(CHUBBY_MAX_IMPACT_RADIUS, CHUBBY_BASE_IMPACT_RADIUS + fallHeight * CHUBBY_RADIUS_PER_FALL_BLOCK)
        val damage = ((fallHeight - CHUBBY_DAMAGE_FALL_OFFSET) * CHUBBY_DAMAGE_PER_FALL_BLOCK)
            .coerceIn(CHUBBY_MIN_IMPACT_DAMAGE, CHUBBY_MAX_IMPACT_DAMAGE)
        val impactPower = ((fallHeight - 3.0) / 14.0).coerceIn(0.0, 1.5)
        val ringPoints = (16 + fallHeight * 1.8).toInt().coerceIn(18, 54)
        val dustCount = (18 + fallHeight * 3.4).toInt().coerceIn(24, 110)
        val cloudCount = (12 + fallHeight * 2.2).toInt().coerceIn(18, 78)
        val particleSpeed = (0.08 + fallHeight * 0.012).coerceAtMost(0.34)
        val soundVolume = (0.65 + fallHeight * 0.04).toFloat().coerceAtMost(1.45f)
        val soundPitch = (1.0 - fallHeight * 0.025).toFloat().coerceAtLeast(0.45f)
        val impact = player.location.clone()
        playerData.radius(impact, TargetType.Enemy, radius, false, hitAttackableObjects = true).forEach { target ->
            target.damage(damage, DamageType.Normal, playerData, damagePath = DamagePath.SKILL)
            var direction = target.entity.boundingBox.center.clone().subtract(player.boundingBox.center).setY(0.0)
            if (direction.lengthSquared() < 1.0E-8) direction = Vector(1.0, 0.0, 0.0)
            target.entity.velocity = direction.normalize()
                .multiply((CHUBBY_BASE_HORIZONTAL_KNOCKBACK + fallHeight * CHUBBY_HORIZONTAL_KNOCKBACK_PER_BLOCK)
                    .coerceAtMost(CHUBBY_MAX_HORIZONTAL_KNOCKBACK))
                .setY((CHUBBY_BASE_VERTICAL_KNOCKBACK + fallHeight * CHUBBY_VERTICAL_KNOCKBACK_PER_BLOCK)
                    .coerceAtMost(CHUBBY_MAX_VERTICAL_KNOCKBACK))
        }
        particles.circle(impact.clone().add(0.0, 0.12, 0.0), Particle.EXPLOSION, radius, ringPoints)
        particles.spawn(impact, Particle.DUST_PLUME, count = dustCount, spread = radius * (0.5 + impactPower * 0.18), speed = particleSpeed)
        particles.spawn(impact, Particle.CLOUD, count = cloudCount, spread = radius * (0.38 + impactPower * 0.16), speed = particleSpeed * 1.25)
        if (fallHeight >= 10.0) {
            particles.spawn(
                impact.clone().add(0.0, 0.25, 0.0),
                Particle.EXPLOSION_EMITTER,
                count = if (fallHeight >= 18.0) 2 else 1,
                spread = impactPower * 0.35,
            )
            sounds.play(impact, Sound.ENTITY_WITHER_BREAK_BLOCK, volume = soundVolume * 0.65f, pitch = soundPitch * 0.9f)
        }
        sounds.play(impact, Sound.ENTITY_GENERIC_EXPLODE, volume = soundVolume, pitch = soundPitch)
        sounds.play(impact, Sound.ENTITY_RAVAGER_STEP, volume = soundVolume, pitch = (soundPitch * 0.82f).coerceAtLeast(0.4f))
    }

    private class Passive : BasePassive() {
        override val name = "<bold>돼애애애지"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>최대 체력이 100% 증가한다.",
            "<gray>이동 속도가 20% 감소한다.",
            "<gray>플레이어의 크기가 증가한다.",
            "<gray>2칸 이상 높이에서 낙하 시 주변 적을 공중에 띄우고 밀쳐내며 낙하 높이에 비례한 피해를 입힌다."
        )
    }
}
