package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.event.PlayerStatusEffectDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getDamageTakenModifier
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class OnEntityStatusEffectDamageByEntityEvent : Listener {

    @EventHandler
    fun onPlayerDamage(event: PlayerStatusEffectDamageByPlayerEvent) {
        val damagerData = event.damager
        val entityData = event.entity

        if (!isGaming()) return

        val damagerClass = damagerData.gameClass ?: return
        val entityClass = damagerData.gameClass ?: return
        val damagerPassives = damagerClass.passives
        val entityPassives = entityClass.passives
        val damagerSkills = damagerClass.skills
        val entitySkills = entityClass.skills

        // 패시브 적용
        damagerPassives.forEach { passive ->
            if (passive is OnHitHandler) {
                passive.onStatusEffectAttackHit(event)
                passive.onStatusEffectHit(event)
            }
        }
        entityPassives.forEach { passive ->
            if (passive is WhenHitHandler) {
                passive.whenStatusEffectAttackHit(event)
                passive.whenStatusEffectHit(event)
            }
        }

        // 스킬 패시브 적용
        damagerSkills.forEach { skill ->
            if (skill is OnHitHandler) {
                skill.onStatusEffectAttackHit(event)
                skill.onStatusEffectHit(event)
            }
        }
        entitySkills.forEach { skill ->
            if (skill is WhenHitHandler) {
                skill.whenStatusEffectAttackHit(event)
                skill.whenStatusEffectHit(event)
            }
        }

        // 받피증감
        val damageTakenModifier = entityData.getDamageTakenModifier()
        event.addDamageTakenMultiplier(damageTakenModifier.combinedMultiplier)

        if (event.damage <= 0.0) {
            event.isCancelled = true
            return
        }

        // 무적 시간 제거
        entityData.player.noDamageTicks = 0
    }
}
