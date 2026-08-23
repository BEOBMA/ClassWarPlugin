package org.beobma.classWarPlugin.manager

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.list.Parasite
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getDamageTakenModifier
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.status.handler.StatusOnHitHandler
import org.beobma.classWarPlugin.status.handler.StatusWhenHitHandler
import org.beobma.classWarPlugin.status.list.Shield
import org.beobma.classWarPlugin.status.list.Disarm
import org.beobma.classWarPlugin.status.list.Invincibility
import org.bukkit.entity.Entity
import java.util.UUID
import kotlin.math.roundToInt

object DamageManager {
    private const val BASIC_ATTACK_DAMAGE_MULTIPLIER = 0.6

    data class Attribution(
        val attackerId: UUID,
        val attackerName: String,
        val path: DamagePath,
        val recordedAtTick: Long,
    )

    private val lastDamageByTarget: MutableMap<UUID, Attribution> = mutableMapOf()

    fun process(context: DamageContext): Boolean {
        if (context.damage <= 0.0) return false
        if (context.attacker.gameClasses.filterIsInstance<Parasite>().any { it.isParasitizing() }) return false
        if ((context.target as? PlayerData)?.gameClasses?.filterIsInstance<Parasite>()
                ?.any { it.isParasitizing() } == true
        ) return false
        if (context.target.hasStatus<Invincibility>()) return false
        val targetPlayer = context.target as? PlayerData
        if (targetPlayer != null && context.attacker != targetPlayer && !context.attacker.isEnemyOf(targetPlayer)) {
            return false
        }

        val attackerStatus = context.attacker.entityStatus
        val targetStatus = context.target.entityStatus
        val canDamage = when {
            context.path.isBasicAttack ->
                attackerStatus.canAttack && !context.attacker.hasStatus<Disarm>() && targetStatus.isAttackable
            context.path == DamagePath.SKILL -> attackerStatus.canSkillUse && targetStatus.isSkillTargeting
            else -> targetStatus.isSkillTargeting
        }
        if (!canDamage) return false

        dispatchHandlers(context)
        if (context.isCancelled) return false

        if (context.path.isBasicAttack) {
            context.addDamageDealtMultiplier(BASIC_ATTACK_DAMAGE_MULTIPLIER)
        }
        context.addDamageTakenMultiplier(context.target.getDamageTakenModifier().combinedMultiplier)
        applyShield(context)
        return !context.isCancelled && context.damage > 0.0
    }

    fun recordSuccessfulDamage(context: DamageContext) {
        val target = context.target.entity
        CombatManager.recordSuccessfulDamage(context)
        lastDamageByTarget[target.uniqueId] = Attribution(
            context.attacker.uniqueId,
            context.attacker.player.name,
            context.path,
            target.world.fullTime,
        )
    }

    fun consumeAttribution(target: Entity): Attribution? {
        val attribution = lastDamageByTarget.remove(target.uniqueId) ?: return null
        return attribution.takeIf { target.world.fullTime - it.recordedAtTick <= 200L }
    }

    fun clearAttributions(targetIds: Iterable<UUID>) {
        targetIds.forEach(lastDamageByTarget::remove)
    }

    private fun dispatchHandlers(context: DamageContext) {
        val targetPlayer = context.target as? PlayerData

        context.attacker.gameClasses.forEach { gameClass ->
            (gameClass as? OnHitHandler)?.dispatchOnHit(context)
            gameClass.passives.filterIsInstance<OnHitHandler>().forEach { it.dispatchOnHit(context) }
            gameClass.skills.filterIsInstance<OnHitHandler>().forEach { it.dispatchOnHit(context) }
        }
        targetPlayer?.gameClasses?.forEach { gameClass ->
            (gameClass as? WhenHitHandler)?.dispatchWhenHit(context)
            gameClass.passives.filterIsInstance<WhenHitHandler>().forEach { it.dispatchWhenHit(context) }
            gameClass.skills.filterIsInstance<WhenHitHandler>().forEach { it.dispatchWhenHit(context) }
        }

        context.attacker.statusAbnormalitys.filterIsInstance<OnHitHandler>()
            .forEach { it.dispatchOnHit(context) }
        context.target.statusAbnormalitys.filterIsInstance<WhenHitHandler>()
            .forEach { it.dispatchWhenHit(context) }

        if (context.path.isBasicAttack) {
            context.attacker.statusAbnormalitys.filterIsInstance<StatusOnHitHandler>()
                .forEach { it.onAttackHit(context) }
            context.target.statusAbnormalitys.filterIsInstance<StatusWhenHitHandler>()
                .forEach { it.whenAttackHit(context) }
        }
    }

    private fun OnHitHandler.dispatchOnHit(context: DamageContext) {
        onHit(context)
        when (context.path) {
            DamagePath.BASIC_ATTACK, DamagePath.RANGED_ATTACK -> onAttackHit(context)
            DamagePath.SKILL -> onSkillAttackHit(context)
            DamagePath.STATUS_EFFECT -> onStatusEffectAttackHit(context)
        }
    }

    private fun WhenHitHandler.dispatchWhenHit(context: DamageContext) {
        whenHit(context)
        when (context.path) {
            DamagePath.BASIC_ATTACK, DamagePath.RANGED_ATTACK -> whenAttackHit(context)
            DamagePath.SKILL -> whenSkillAttackHit(context)
            DamagePath.STATUS_EFFECT -> whenStatusEffectAttackHit(context)
        }
    }

    private fun applyShield(context: DamageContext) {
        if (context.bypassShield) return
        val shield = context.target.getStatus<Shield>() ?: return
        val damage = context.damage.roundToInt()
        val remainingDamage = (damage - shield.power).coerceAtLeast(0)
        val remainingShield = (shield.power - damage).coerceAtLeast(0)
        context.applyShieldedDamage(remainingDamage.toDouble())
        if (remainingShield == 0) shield.remove() else shield.updatePower(remainingShield)
    }
}
