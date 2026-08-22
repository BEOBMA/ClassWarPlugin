package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.damage.DamageContext
import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.DamageManager
import org.beobma.classWarPlugin.manager.DamageIndicatorManager
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.util.DamageType
import org.beobma.classWarPlugin.gameClass.list.Vampire
import org.bukkit.entity.Player
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

class OnEntityDamageByEntityEvent : Listener {
    @EventHandler
    fun onPlayerDamage(event: EntityDamageByEntityEvent) {
        if (Vampire.handleBatDamage(event)) return
        if (event.damage < 1.0) {
            event.isCancelled = true
            return
        }

        val directDamager = event.damager
        val attacker = when (directDamager) {
            is Player -> directDamager
            is Projectile -> directDamager.shooter as? Player
            else -> null
        } ?: return
        val path = if (directDamager is Projectile) DamagePath.RANGED_ATTACK else DamagePath.BASIC_ATTACK

        val targetEntity = event.entity as? LivingEntity ?: return
        val isMannequin = targetEntity.isMannequin()
        val targetPlayer = targetEntity as? Player
        val attackerIsTraining = PlayerTagManager.hasTag(attacker, "isTraining")
        if (targetPlayer == null && !isMannequin && !attackerIsTraining) return
        if (!isGaming() &&
            !attackerIsTraining &&
            !(targetPlayer != null && PlayerTagManager.hasTag(targetPlayer, "isTraining"))
        ) return

        val attackerGame = findGameForPlayer(attacker) ?: return
        val attackerData = attackerGame.playerDatas.filterIsInstance<PlayerData>()
            .find { it.uniqueId == attacker.uniqueId } ?: run {
            event.isCancelled = true
            return
        }

        val targetData = if (isMannequin) {
            attackerGame.playerDatas.find { it.entity.uniqueId == targetEntity.uniqueId }
                ?: DummyEntityData(targetEntity, attackerGame).also { attackerGame.playerDatas.add(it) }
        } else if (targetPlayer == null) {
            attackerGame.playerDatas.find { it.entity.uniqueId == targetEntity.uniqueId }
                ?: MobEntityData(targetEntity, attackerGame).also { attackerGame.playerDatas.add(it) }
        } else {
            val player = targetPlayer
            val targetGame = findGameForPlayer(player) ?: return
            if (attackerGame !== targetGame) {
                event.isCancelled = true
                return
            }
            targetGame.playerDatas.filterIsInstance<PlayerData>()
                .find { it.uniqueId == player.uniqueId } ?: run {
                event.isCancelled = true
                return
            }
        }

        val context = DamageContext(
            attacker = attackerData,
            target = targetData,
            path = path,
            damageType = DamageType.Normal,
            baseDamage = event.damage,
        )
        if (!DamageManager.process(context)) {
            event.isCancelled = true
            return
        }

        if (isMannequin) {
            event.isCancelled = true
            targetEntity.playHurtAnimation(0.0f)
            DamageIndicatorManager.show(targetEntity, context.damage, attackerGame.settings.damageIndicatorsEnabled)
            val formattedDamage = String.format("%.2f", context.damage)
            attacker.sendMiniMessage(
                "<gray>피해 경로: ${path.displayName} <gray>피해량: <gold><bold>$formattedDamage</bold></gold>"
            )
            return
        }

        event.damage = context.damage
        if (targetPlayer == null) {
            DamageIndicatorManager.show(targetEntity, event.finalDamage, attackerGame.settings.damageIndicatorsEnabled)
        }
        DamageManager.recordSuccessfulDamage(context)
        targetPlayer?.noDamageTicks = 0
    }
}
