package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent

interface OnHitHandler {
    fun onHit(skillDamageEvent: PlayerSkillDamageByPlayerEvent?, attackDamageEvent: EntityDamageByEntityEvent?)

    fun onAttackHit(event: EntityDamageByEntityEvent)

    fun onSkillAttackHit(event: PlayerSkillDamageByPlayerEvent)
}