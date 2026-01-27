package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.gameClass.OnHitHandler
import org.beobma.classWarPlugin.gameClass.WhenHitHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.GameManager.findGameForPlayer
import org.beobma.classWarPlugin.manager.MannequinStatusManager
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getDamageTakenModifier
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.status.StatusOnHitHandler
import org.beobma.classWarPlugin.util.addDamageTakenMultiplier
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
        if (event.damage < 1) {
            event.isCancelled = true
            return
        }
        
        if (damager !is Player) return
        val isMannequin = entity.isMannequin()
        if (entity !is Player && !isMannequin) return
        if (!isGaming()
            && !PlayerTagManager.hasTag(damager, "isTraining")
            && !(entity is Player && PlayerTagManager.hasTag(entity, "isTraining"))
        ) {
            return
        }

        val damagerGame = findGameForPlayer(damager) ?: return
        val damagerData = damagerGame.playerDatas.find { it.player == damager } ?: return
        val damagerStatus = damagerData.playerStatus

        if (isMannequin) {
            val mannequinStatus = MannequinStatusManager.getStatus(entity) ?: return
            if (!damagerStatus.canAttack || !mannequinStatus.isAttackable) {
                event.isCancelled = true
                return
            }

            val damagerClass = damagerData.gameClass ?: return
            val damagerPassives = damagerClass.passives
            val damagerSkills = damagerClass.skills

            // 패시브 적용
            damagerPassives.forEach { passive ->
                if (passive is OnHitHandler) {
                    passive.onAttackHit(event)
                    passive.onHit(null, event)
                }
            }

            // 스킬 패시브 적용
            damagerSkills.forEach { skill ->
                if (skill is OnHitHandler) {
                    skill.onAttackHit(event)
                    skill.onHit(null, event)
                }
            }

            event.isCancelled = true
            val formattedDamage = String.format("%.2f", event.damage)
            damager.sendMiniMessage("<gray>피해 경로: <yellow><bold>기본 공격</bold></yellow> <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
            return
        }

        val entityPlayer = entity as Player
        val entityGame = findGameForPlayer(entityPlayer) ?: return
        if (damagerGame != entityGame) return
        val entityData = entityGame.playerDatas.find { it.player == entityPlayer } ?: return
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
        entityData.statusAbnormalitys.forEach { status ->
            if (status is StatusOnHitHandler) {
                status.onAttackHit(event, damagerData, entityData)
            }
        }

        // 받피증감
        val damageTakenModifier = entityData.getDamageTakenModifier()
        event.addDamageTakenMultiplier(damageTakenModifier.combinedMultiplier)

        if (entityPlayer.isMannequin()) {
            event.isCancelled = true
            val formattedDamage = String.format("%.2f", event.damage)
            damager.sendMiniMessage("<gray>가한 피해 정보 - 피해 경로: <yellow><bold>기본 공격</bold></yellow> <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
            return
        }

        if (event.damage <= 0.0) {
            event.isCancelled = true
            return
        }

        // 무적 시간 제거
        entity.noDamageTicks = 0
    }
}
