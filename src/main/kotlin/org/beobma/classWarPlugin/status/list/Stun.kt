package org.beobma.classWarPlugin.status.list

import org.beobma.classWarPlugin.entity.player.PlayerData
import org.beobma.classWarPlugin.keyword.Keyword
import org.beobma.classWarPlugin.status.StatusAbnormality
import org.beobma.classWarPlugin.status.StatusDurationMode
import org.beobma.classWarPlugin.status.handler.StatusOnHitHandler
import org.beobma.classWarPlugin.status.handler.StatusPlayerMoveHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerMoveEvent

class Stun : StatusAbnormality(), StatusOnHitHandler, StatusPlayerMoveHandler {
    override val name: String
        get() = Keyword.Stun.string
    override val description: List<String>
        get() = listOf(
            Keyword.Stun.description!!,
            "",
            "<gray>수치 없음",
            "<gray>지속시간 연장",
            "<gray>지속시간 종료 시 소멸"
        )
    override val canRemove: Boolean = true
    override var maxPower: Int? = 1
    override var duration: Int? = null
    override val durationMode: StatusDurationMode = StatusDurationMode.Extend
    override val showMaxPower: Boolean = false
    override val showPower: Boolean = false

    override fun onAttackHit(
        event: EntityDamageByEntityEvent,
        damagerData: PlayerData,
        entityData: PlayerData
    ) {
        event.isCancelled = true
    }

    override fun onPlayerMove(
        event: PlayerMoveEvent,
        playerData: PlayerData
    ) {
        event.isCancelled = true
    }
}