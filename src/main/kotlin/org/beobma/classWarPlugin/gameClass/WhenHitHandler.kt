package org.beobma.classWarPlugin.gameClass

import org.beobma.classWarPlugin.event.PlayerSkillDamageByPlayerEvent
import org.beobma.classWarPlugin.event.PlayerStatusEffectDamageByPlayerEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent

interface WhenHitHandler {
    fun whenHit(skillDamageEvent: PlayerSkillDamageByPlayerEvent?, attackDamageEvent: EntityDamageByEntityEvent?)

    fun whenAttackHit(event: EntityDamageByEntityEvent)

    fun whenSkillAttackHit(event: PlayerSkillDamageByPlayerEvent)

    fun whenStatusEffectHit(event: PlayerStatusEffectDamageByPlayerEvent) {}

    fun whenStatusEffectAttackHit(event: PlayerStatusEffectDamageByPlayerEvent) {}
}
