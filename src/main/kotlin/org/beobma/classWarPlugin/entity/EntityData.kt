package org.beobma.classWarPlugin.entity

import org.beobma.classWarPlugin.damage.DamagePath
import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.util.DamageType
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask

/**
 * 플레이어, 훈련용 몹 등 전투 시스템이 다루는 Bukkit 엔티티의 공통 런타임 상태다.
 * 등록된 [bukkitTasks]와 [statusAbnormalitys]는 해당 엔티티의 수명주기에 맞춰 정리된다.
 */
abstract class EntityData {
    abstract val entity: Entity
    abstract val game: Game
    abstract val entityStatus: EntityStatus
    abstract val bukkitTasks: MutableList<BukkitTask>
    abstract val statusAbnormalitys: MutableList<StatusAbnormality>
}

/** 플레이어가 소유하며 소유자의 적대 관계를 그대로 따르는 별도 전투 대상입니다. */
interface PlayerOwnedEntityData {
    val ownerData: PlayerData
}

/** 받은 피해를 실제 소유자 등 다른 전투 대상으로 전달하는 엔티티입니다. */
interface DamageRedirectEntityData {
    fun redirectDamage(
        damage: Double,
        damageType: DamageType,
        damager: PlayerData,
        isInvincibilityTimeIgnore: Boolean,
        bypassShield: Boolean,
        damagePath: DamagePath?,
        armorIgnoreRatio: Double,
    )
}
