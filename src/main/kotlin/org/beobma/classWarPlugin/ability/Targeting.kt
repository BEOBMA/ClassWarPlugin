package org.beobma.classWarPlugin.ability

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.PlayerOwnedEntityData
import org.beobma.classWarPlugin.entity.dummy.DummyEntityData
import org.beobma.classWarPlugin.entity.mob.MobEntityData
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.manager.PlayerTagManager
import org.beobma.classWarPlugin.manager.StatusAbnormalityManager.hasStatus
import org.beobma.classWarPlugin.manager.UtilManager.isMannequin
import org.beobma.classWarPlugin.status.list.Stealth
import org.beobma.classWarPlugin.util.TargetType
import org.bukkit.World
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/** Registration is a training-session update; all combat queries are read-only snapshots. */
object Targeting {
    fun synchronizeTraining(source: PlayerData) {
        val game = source.game
        val known = game.playerDatas.mapTo(HashSet()) { it.entity.uniqueId }
        source.player.world.livingEntities.forEach { entity ->
            if (entity !is Player && entity.isValid && !entity.isDead && known.add(entity.uniqueId)) {
                game.playerDatas.add(if (entity.isMannequin()) DummyEntityData(entity, game) else MobEntityData(entity, game))
            }
        }
    }

    fun candidates(source: EntityData, world: World = source.entity.world): List<EntityData> =
        source.game.playerDatas.filter { candidate ->
            val entity = candidate.entity
            entity is LivingEntity && entity.world == world && entity.isValid && !entity.isDead &&
                (entity !is Player || entity.isOnline) && !candidate.entityStatus.isDead &&
                candidate.entityStatus.isSkillTargeting
        }.distinctBy { it.entity.uniqueId }

    fun isEnemy(source: EntityData, candidate: EntityData): Boolean {
        val player = when (source) {
            is PlayerData -> source
            is PlayerOwnedEntityData -> source.ownerData
            else -> return false
        }
        return when (candidate) {
            is PlayerData -> player.isEnemyOf(candidate)
            is PlayerOwnedEntityData -> player.isEnemyOf(candidate.ownerData)
            else -> PlayerTagManager.isTraining(player.player)
        }
    }

    fun select(source: EntityData, type: TargetType, world: World = source.entity.world,
               includeSelf: Boolean = false, includeStealth: Boolean = true): List<EntityData> =
        candidates(source, world).filter { candidate ->
            (includeSelf || candidate != source) && (includeStealth || !candidate.hasStatus<Stealth>()) &&
                when (type) {
                    TargetType.Self -> candidate == source
                    TargetType.Enemy -> isEnemy(source, candidate)
                    TargetType.All -> true
                }
        }
}
