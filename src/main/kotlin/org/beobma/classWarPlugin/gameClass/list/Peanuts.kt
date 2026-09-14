package org.beobma.classWarPlugin.gameClass.list

import org.beobma.classWarPlugin.ClassWarPlugin
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.GameClass
import org.beobma.classWarPlugin.gameClass.Rank
import org.beobma.classWarPlugin.gameClass.handler.GameEndHandler
import org.beobma.classWarPlugin.gameClass.handler.GameStatusHandler
import org.beobma.classWarPlugin.gameClass.handler.PlayerDeathHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.addStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.applyStatus
import org.beobma.classWarPlugin.skill.Passive as BasePassive
import org.beobma.classWarPlugin.skill.Skill
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.list.MoveSpeedDecrease
import org.beobma.classWarPlugin.status.list.MoveSpeedIncrease
import org.beobma.classWarPlugin.util.HitboxUtil
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.beobma.classWarPlugin.ability.AbilityRunnable as BukkitRunnable
import kotlin.math.cos

private const val PEANUTS_VIEW_RANGE = 48.0
private const val PEANUTS_VIEW_HALF_ANGLE_DEGREES = 42.0

class Peanuts : GameClass(), GameStatusHandler, GameEndHandler, PlayerDeathHandler,
    org.beobma.classWarPlugin.gameClass.handler.OnHitHandler,
    org.beobma.classWarPlugin.gameClass.handler.ConfirmedHitHandler {
    override val classId = "peanuts"
    override val name = "<gray>땅콩이"
    override val rank = Rank.B
    override val classItemMaterial = Material.RABBIT_HIDE
    override var skills: List<Skill> = emptyList()
    override var passives: List<BasePassive> = listOf(Passive(), PassiveTwo())

    private var speedStatus: StatusAbnormality? = null
    private var watched: Boolean? = null
    private val nextNeckBreak = mutableMapOf<java.util.UUID, Long>()
    private val pendingNeckBreaks = java.util.WeakHashMap<org.beobma.classWarPlugin.damage.DamageContext, Boolean>()

    private fun isWatchedNow(): Boolean = game.playerDatas.asSequence().filterIsInstance<PlayerData>()
        .filter { it != playerData && it.player.isOnline && !it.entityStatus.isDead }
        .any(::canSeeMe)

    override fun onHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if (context.isCancelled || context.damageType.isFixed || context.secondaryAttack ||
            !org.beobma.classWarPlugin.ability.Targeting.isEnemy(playerData, context.target) || isWatchedNow()) return
        val victim = context.target.entity as? org.bukkit.entity.LivingEntity ?: return
        if (game.combatTick < (nextNeckBreak[victim.uniqueId] ?: Long.MIN_VALUE)) return
        val maximum = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: return
        // Replace, rather than add, the attack. Compensate only the common basic-attack normalization;
        // ordinary armor, shields, and damage modifiers still apply (this is not fixed damage).
        val normalization = if (context.path.isBasicAttack)
            org.beobma.classWarPlugin.manager.DamageManager.BASIC_ATTACK_DAMAGE_MULTIPLIER else 1.0
        context.addBaseDamage(maximum * 0.25 / normalization - context.originalDamage)
        pendingNeckBreaks[context] = true
    }

    override fun onConfirmedHit(context: org.beobma.classWarPlugin.damage.DamageContext) {
        if (pendingNeckBreaks.remove(context) != true) return
        nextNeckBreak[context.target.entity.uniqueId] = game.combatTick + 400L
        val neck = context.target.entity.boundingBox.let { box ->
            org.bukkit.Location(context.target.entity.world, box.centerX, box.maxY - box.height * 0.2, box.centerZ)
        }
        particles.spawn(neck, Particle.CRIT, count = 10, spread = 0.15, speed = 0.05)
        sounds.play(neck, Sound.ENTITY_SKELETON_HURT, volume = 0.8f, pitch = 0.65f)
        sounds.play(neck, Sound.BLOCK_BONE_BLOCK_BREAK, volume = 0.6f, pitch = 0.85f)
    }

    override fun onBattleStart() {
        nextNeckBreak.clear()
        pendingNeckBreaks.clear()
        refreshVisibilityState()
        playerData.trackTask(object : BukkitRunnable(abilityScope) {
            override fun run() {
                if (!player.isOnline || playerStatus.isDead) {
                    clearSpeed()
                    cancel()
                    return
                }
                refreshVisibilityState()
            }
        }.runTaskTimer(ClassWarPlugin.instance, 0L, 2L))
    }

    override fun onGameTimePasses() = Unit
    override fun onGameEnd() = clearSpeed()
    override fun onPlayerDeath() = clearSpeed()

    private fun refreshVisibilityState() {
        val isWatched = isWatchedNow()
        if (watched == isWatched && (speedStatus?.power ?: 0) > 0) return
        watched = isWatched
        clearSpeed()
        speedStatus = if (isWatched) {
            playerData.addStatus(MoveSpeedDecrease(), playerData).also { it.applyStatus(powerSet = 90) }
        } else {
            playerData.addStatus(MoveSpeedIncrease(), playerData).also { it.applyStatus(powerSet = 173) }
        }
        if (isWatched) {
            particles.spawn(player, Particle.SMOKE, count = 10, spread = 0.35, speed = 0.02)
            sounds.playTo(player, Sound.ENTITY_ENDERMAN_STARE, volume = 0.35f, pitch = 1.75f)
        } else {
            particles.spawn(player, Particle.CLOUD, count = 12, spread = 0.4, speed = 0.06)
            sounds.playTo(player, Sound.ENTITY_RABBIT_JUMP, volume = 0.55f, pitch = 1.6f)
        }
    }

    private fun canSeeMe(observer: PlayerData): Boolean {
        if (observer.player.world != player.world || !observer.player.hasLineOfSight(player)) return false
        val eye = observer.player.eyeLocation
        if (HitboxUtil.distanceSquared(player.boundingBox, eye.toVector()) > PEANUTS_VIEW_RANGE * PEANUTS_VIEW_RANGE) return false
        val point = HitboxUtil.closestPoint(player.boundingBox, eye.toVector())
        val toTarget = point.subtract(eye.toVector())
        if (toTarget.lengthSquared() <= 1.0E-8) return true
        return eye.direction.normalize().dot(toTarget.normalize()) >= cos(Math.toRadians(PEANUTS_VIEW_HALF_ANGLE_DEGREES))
    }

    private fun clearSpeed() {
        speedStatus?.remove()
        speedStatus = null
    }

    private class Passive : BasePassive() {
        override val name = "<bold>173"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>다른 플레이어의 시야 범위에 있지 않을 때",
            "<gray>자신의 <gold><bold>이동 속도가 173% 증가</bold><gray>한다.", "",
            "<gray>다른 플레이어의 시야 범위에 있을 때",
            "<gray>자신의 <gold><bold>이동 속도가 90% 감소</bold><gray>한다."
        )
    }

    private class PassiveTwo : BasePassive() {
        override val name = "<bold>목 꺾기"
        override val description = listOf(
            "<gray>패시브", "",
            "<gray>다른 플레이어의 시야 범위에 있지 않을 때",
            "<gray>가하는 피해는 적의 최대 체력의 25%에 해당하는 피해를 입힌다. (대상 당 재사용 대기 시간 20초)"
        )
    }
}
