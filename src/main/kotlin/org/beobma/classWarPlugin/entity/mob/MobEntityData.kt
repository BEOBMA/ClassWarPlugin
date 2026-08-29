package org.beobma.classWarPlugin.entity.mob

import org.beobma.classWarPlugin.entity.EntityData
import org.beobma.classWarPlugin.entity.EntityStatus
import org.beobma.classWarPlugin.game.Game
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitTask

/** 훈련 중 일반 생명체를 스킬 대상과 피해 처리에 연결한다. */
class MobEntityData(
    override val entity: LivingEntity,
    override val game: Game,
) : EntityData() {
    override val entityStatus: EntityStatus = object : EntityStatus() {}
    override val bukkitTasks: MutableList<BukkitTask> = mutableListOf()
    override val statusAbnormalitys: MutableList<StatusAbnormality> = mutableListOf()
}
