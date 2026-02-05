package org.beobma.classWarPlugin.listener

import org.beobma.classWarPlugin.event.PlayerStatusEffectDamageByPlayerEvent
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.gameClass.handler.OnHitHandler
import org.beobma.classWarPlugin.gameClass.handler.WhenHitHandler
import org.beobma.classWarPlugin.info.Info.isGaming
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.getDamageTakenModifier
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.manager.UtilManager.sendMiniMessage
import org.beobma.classWarPlugin.manager.forEachIs
import org.beobma.classWarPlugin.util.DamageType.Normal
import org.beobma.classWarPlugin.util.DamageType.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType.True
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class OnEntityStatusEffectDamageByEntityEvent : Listener {

    @EventHandler
    fun onPlayerDamage(event: PlayerStatusEffectDamageByPlayerEvent) {
        val damagerData = event.damager
        val entityData = event.entity

        if (!isGaming()) return

        val damagerClass = damagerData.gameClass ?: return
        val entityClass = (entityData as? PlayerData)?.gameClass
        val damagerPassives = damagerClass.passives
        val entityPassives = entityClass?.passives.orEmpty()
        val damagerSkills = damagerClass.skills
        val entitySkills = entityClass?.skills.orEmpty()

        // 패시브 적용
        damagerPassives.forEachIs<OnHitHandler> { passive ->
            passive.onStatusEffectAttackHit(event)
        }
        entityPassives.forEachIs<WhenHitHandler> { passive ->
            passive.whenStatusEffectAttackHit(event)
        }

        // 스킬 패시브 적용
        damagerSkills.forEachIs<OnHitHandler> { skill ->
            skill.onStatusEffectAttackHit(event)
        }
        entitySkills.forEachIs<WhenHitHandler> { skill ->
            skill.whenStatusEffectAttackHit(event)
        }

        // 받피증감
        val damageTakenModifier = entityData.getDamageTakenModifier()
        event.addDamageTakenMultiplier(damageTakenModifier.combinedMultiplier)

        if (event.damage <= 0.0) {
            event.isCancelled = true
            return
        }

        if (entityData.entity.isMannequin()) {
            event.isCancelled = true
            val formattedDamage = String.format("%.2f", event.damage)
            val damageText = when (event.damageType) {
                Normal -> "<gray>일반 피해</gray>"
                True -> "<white>고정 피해</white>"
                StatusAbnormality -> "<green>상태이상 피해</green>"
            }
            damagerData.player.sendMiniMessage("<gray>가한 상태이상 피해 정보 - 피해 종류: <bold>${damageText}</bold> <gray>피해량: <gold><bold>$formattedDamage</bold></gold>")
            return
        }

        // 무적 시간 제거
        (entityData as? PlayerData)?.player?.noDamageTicks = 0
    }
}
