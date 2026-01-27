package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getDamageTakenModifier
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.manager.forEachIs
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class OnEntitySkillDamageByEntityEvent : Listener {

    @EventHandler
    fun onPlayerDamage(event: PlayerSkillDamageByPlayerEvent) {
        val damagerData = event.damager
        val entityData = event.entity
        val damagerStatus = damagerData.playerStatus
        val entityStatus = damagerData.playerStatus

        // 1보다 작은 피해는 피해를 받지 않은 것으로 간주
        if (event.damage < 1) return
        if (!isGaming() && !PlayerTagManager.hasTag(damagerData.player, "isTraining")) return

        // 공격, 피격 가능 여부
        if (!damagerStatus.canSkillUse || !entityStatus.isSkillTargeting) {
            event.isCancelled = true
            return
        }
        val damagerClass = damagerData.gameClass ?: return
        val entityClass = damagerData.gameClass ?: return
        val damagerPassives = damagerClass.passives
        val entityPassives = entityClass.passives
        val damagerSkills = damagerClass.skills
        val entitySkills = entityClass.skills

        // 패시브 적용
        damagerPassives.forEachIs<OnHitHandler> { passive ->
            passive.onSkillAttackHit(event)
            passive.onHit(event, null)
        }
        entityPassives.forEachIs<WhenHitHandler> { passive ->
            passive.whenSkillAttackHit(event)
            passive.whenHit(event, null)
        }

        // 스킬 패시브 적용
        damagerSkills.forEachIs<OnHitHandler> { skill ->
            skill.onSkillAttackHit(event)
            skill.onHit(event, null)
        }
        entitySkills.forEachIs<WhenHitHandler> { skill ->
            skill.whenSkillAttackHit(event)
            skill.whenHit(event, null)
        }

        // 받피증감
        val damageTakenModifier = entityData.getDamageTakenModifier()
        event.addDamageTakenMultiplier(damageTakenModifier.combinedMultiplier)

        if (entityData.player.isMannequin() && PlayerTagManager.hasTag(damagerData.player, "isTraining")) {
            event.isCancelled = true
            val formattedDamage = String.format("%.2f", event.damage)
            damagerData.player.sendMiniMessage("<gray>Damage path: <yellow>${event.damageType}</yellow> <gray>Damage: <gold>$formattedDamage</gold>")
            return
        }

        if (event.damage <= 0.0) {
            event.isCancelled = true
            return
        }

        // 무적 시간 제거
        entityData.player.noDamageTicks = 0
    }
}
