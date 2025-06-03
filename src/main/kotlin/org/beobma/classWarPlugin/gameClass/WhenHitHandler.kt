package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent

interface WhenHitHandler {
    fun whenHit(skillDamageEvent: PlayerSkillDamageByPlayerEvent?, attackDamageEvent: EntityDamageByEntityEvent?)

    fun whenAttackHit(event: EntityDamageByEntityEvent)

    fun whenSkillAttackHit(event: PlayerSkillDamageByPlayerEvent)
}