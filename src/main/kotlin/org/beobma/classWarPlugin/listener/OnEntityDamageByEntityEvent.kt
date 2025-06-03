package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.info.Info.game
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.PlayerManager.damage
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getStatus
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getWhenDamage
import org.beobma.classWarPlugin.status.list.Bleeding
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

class OnEntityDamageByEntityEvent : Listener {

    @EventHandler
    fun onPlayerDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val entity = event.entity

        // 1보다 작은 피해는 피해를 받지 않은 것으로 간주
        if (event.damage < 1) return
        if (damager !is Player || entity !is Player) return
        if (isGaming()) return
        val damagerData = game?.playerDatas?.find { it.player == damager } ?: return
        val entityData = game?.playerDatas?.find { it.player == entity } ?: return
        val damagerStatus = damagerData.playerStatus
        val entityStatus = entityData.playerStatus

        // 공격, 피격 가능 여부
        if (!damagerStatus.canAttack || !entityStatus.isAttackable) {
            event.isCancelled = true
            return
        }

        val damagerClass = damagerData.gameClass ?: return
        val entityClass = entityData.gameClass ?: return
        val damagerPassives = damagerClass.passives
        val entityPassives = entityClass.passives
        val damagerSkills = damagerClass.skills
        val entitySkills = entityClass.skills

        // 패시브 적용
        damagerPassives.forEach { passive ->
            if (passive is OnHitHandler) {
                passive.onAttackHit(event)
                passive.onHit(null, event)
            }
        }
        entityPassives.forEach { passive ->
            if (passive is WhenHitHandler) {
                passive.whenAttackHit(event)
                passive.whenHit(null, event)
            }
        }

        // 스킬 패시브 적용
        damagerSkills.forEach { skill ->
            if (skill is OnHitHandler) {
                skill.onAttackHit(event)
                skill.onHit(null, event)
            }
        }
        entitySkills.forEach { skill ->
            if (skill is WhenHitHandler) {
                skill.whenAttackHit(event)
                skill.whenHit(null, event)
            }
        }

        // 상태이상 적용
        if (damagerData.statusAbnormalitys.isNotEmpty()) {
            val bleeding = entityData.getStatus<Bleeding>()
            if (bleeding != null){
                entityData.damage(bleeding.power.toDouble(), DamageType.StatusAbnormality, entityData)
                bleeding.updatePower(bleeding.power / 2)
            }
        }

        // 받피증감
        event.damage *= entityData.getWhenDamage()

        if (event.damage <= 0.0) {
            event.isCancelled = true
            return
        }

        // 무적 시간 제거
        entity.noDamageTicks = 0
    }
}