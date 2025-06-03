package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getWhenDamage
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
        if (isGaming()) return

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
        damagerPassives.forEach { passive ->
            if (passive is OnHitHandler) {
                passive.onSkillAttackHit(event)
                passive.onHit(event, null)
            }
        }
        entityPassives.forEach { passive ->
            if (passive is WhenHitHandler) {
                passive.whenSkillAttackHit(event)
                passive.whenHit(event, null)
            }
        }

        // 스킬 패시브 적용
        damagerSkills.forEach { skill ->
            if (skill is OnHitHandler) {
                skill.onSkillAttackHit(event)
                skill.onHit(event, null)
            }
        }
        entitySkills.forEach { skill ->
            if (skill is WhenHitHandler) {
                skill.whenSkillAttackHit(event)
                skill.whenHit(event, null)
            }
        }

        // 받피증감
        event.damage *= entityData.getWhenDamage()

        if (event.damage <= 0.0) {
            event.isCancelled = true
            return
        }

        // 무적 시간 제거
        entityData.player.noDamageTicks = 0
    }
}